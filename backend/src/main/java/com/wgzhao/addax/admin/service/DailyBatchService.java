package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.repository.EtlJobQueueRepo;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Decides whether a business day's automatic collection batch is finished and
 * publishes the once-a-day DAILY summary.
 *
 * <p>Completion is a scheduling fact, not a table status: a retry chain keeps a
 * table in 'E' with a retry budget left, and the queue still owes work while a
 * row is pending or running. The batch is terminal only when there is no active
 * automatic queue row and no due table can still move without a manual action.
 * Tables stuck in 'U' are terminal but counted as failures: they will not collect
 * this period at all.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyBatchService
{
    private static final String REPORTED_KEY_PREFIX = "alert:batch-reported:";
    private static final Duration REPORTED_TTL = Duration.ofDays(7);

    private final EtlTableRepo etlTableRepo;
    private final EtlJobQueueRepo jobQueueRepo;
    private final CalendarService calendarService;
    private final AlertService alertService;
    private final StringRedisTemplate stringRedisTemplate;

    public record Snapshot(LocalDate bizDate, int due, int done, int failed, int blocked,
        int notStarted, int retrying, long activeQueue, List<String> failedTables)
    {
        public boolean terminal()
        {
            return activeQueue == 0 && notStarted == 0 && retrying == 0;
        }
    }

    /**
     * Called every minute on the master for the business period that is open now.
     * The Redis marker keeps the summary to one per business date even if the
     * master changes while the batch is finishing.
     */
    public void checkCurrentPeriod()
    {
        LocalDate date = calendarService.businessPeriodDate();
        if (alreadyReported(date)) {
            return;
        }
        Snapshot snapshot = snapshot(date);
        if (!snapshot.terminal()) {
            return;
        }
        publish(date, snapshot, false);
    }

    /**
     * Called at the start of the day cut, before flags are reset. The period that
     * is closing may never have reached a terminal state; its table statuses are
     * about to be overwritten, so the summary has to be produced now.
     */
    public void reportClosingIfNeeded(LocalDate closingDate)
    {
        try {
            if (alreadyReported(closingDate)) {
                return;
            }
            publish(closingDate, snapshot(closingDate), true);
        }
        catch (Exception ex) {
            log.warn("Failed to report the closing business date {}", closingDate, ex);
        }
    }

    public Snapshot snapshot(LocalDate date)
    {
        List<String> dueCalendars = etlTableRepo.findCalendarsInUse().stream()
            .filter(calendar -> calendarService.evaluate(calendar, date).due())
            .toList();
        if (dueCalendars.isEmpty()) {
            return new Snapshot(date, 0, 0, 0, 0, 0, 0, 0, List.of());
        }
        List<EtlTable> dueTables = etlTableRepo.findDueCandidates(dueCalendars);
        int done = 0;
        int failed = 0;
        int blocked = 0;
        int notStarted = 0;
        int retrying = 0;
        List<String> failedTables = new ArrayList<>();
        for (EtlTable table : dueTables) {
            String status = table.getStatus();
            if (TableStatus.COLLECTED.equals(status)) {
                done++;
            }
            else if (TableStatus.COLLECT_FAIL.equals(status)) {
                // A failed row with retry budget left is still in its retry chain:
                // the scan will enqueue it again, so the batch is not terminal.
                if (table.getRetryCnt() != null && table.getRetryCnt() > 0) {
                    retrying++;
                }
                else {
                    failed++;
                    failedTables.add(tableDesc(table));
                }
            }
            else if (TableStatus.WAIT_SCHEMA.equals(status)) {
                blocked++;
                failedTables.add(tableDesc(table) + "（等待更新）");
            }
            else if (TableStatus.NOT_COLLECT.equals(status) || TableStatus.WAITING_COLLECT.equals(status)
                || TableStatus.COLLECTING.equals(status)) {
                notStarted++;
            }
        }
        long activeQueue = jobQueueRepo.countActiveAutomatic(date);
        return new Snapshot(date, dueTables.size(), done, failed, blocked, notStarted, retrying,
            activeQueue, failedTables);
    }

    private void publish(LocalDate date, Snapshot snapshot, boolean closing)
    {
        if (!markReported(date)) {
            return;
        }
        boolean allGood = snapshot.failed() == 0 && snapshot.blocked() == 0;
        String title;
        String message;
        if (snapshot.due() == 0) {
            title = "当日无采集任务 · " + date;
            message = "业务日 " + date + " 没有任何到期的采集表（节假日、日历未覆盖或全部处于停采）。";
        }
        else if (snapshot.terminal() && allGood) {
            title = "当日采集全部完成 · " + date;
            message = "业务日 " + date + " 的自动采集已全部完成：应采 " + snapshot.due()
                + " 张，成功 " + snapshot.done() + " 张。";
        }
        else if (snapshot.terminal()) {
            title = "当日采集完成但有失败 · " + date;
            message = "业务日 " + date + " 的自动采集已结束：应采 " + snapshot.due()
                + " 张，成功 " + snapshot.done() + " 张，失败 " + snapshot.failed()
                + " 张，等待更新(U) " + snapshot.blocked() + " 张。失败表需要人工处理；之后可通过补数补齐。";
            message += failureNames(snapshot.failedTables());
        }
        else {
            // Only the closing report sees a non-terminal snapshot: the period is
            // ending while work is still owed.
            title = "当日采集结束但未全部完成 · " + date;
            message = "业务日 " + date + " 结束时自动采集仍未全部完成：应采 " + snapshot.due()
                + " 张，成功 " + snapshot.done() + " 张，失败 " + snapshot.failed()
                + " 张，等待更新(U) " + snapshot.blocked() + " 张，未开始 " + snapshot.notStarted()
                + " 张，重试中 " + snapshot.retrying() + " 张，队列中 " + snapshot.activeQueue()
                + " 张。未完成的表会在新业务日重新调度，但本业务日的数据不完整，需确认后补数。";
            message += failureNames(snapshot.failedTables());
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("bizDate", date.toString());
        data.put("closing", String.valueOf(closing));
        data.put("due", String.valueOf(snapshot.due()));
        data.put("done", String.valueOf(snapshot.done()));
        data.put("failed", String.valueOf(snapshot.failed()));
        data.put("blocked", String.valueOf(snapshot.blocked()));
        data.put("notStarted", String.valueOf(snapshot.notStarted()));
        data.put("retrying", String.valueOf(snapshot.retrying()));
        data.put("activeQueue", String.valueOf(snapshot.activeQueue()));
        alertService.sendDailySummary("BATCH:" + date, title, message, data);
    }

    private String failureNames(List<String> names)
    {
        if (names.isEmpty()) {
            return "";
        }
        return "\n" + names.stream().limit(10).collect(Collectors.joining("、"))
            + (names.size() > 10 ? " 等 " + names.size() + " 张" : "");
    }

    private boolean alreadyReported(LocalDate date)
    {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(REPORTED_KEY_PREFIX + date));
        }
        catch (Exception ex) {
            log.warn("Failed to read the batch-report marker for {}", date, ex);
            return false;
        }
    }

    private boolean markReported(LocalDate date)
    {
        try {
            Boolean fresh = stringRedisTemplate.opsForValue()
                .setIfAbsent(REPORTED_KEY_PREFIX + date, "1", REPORTED_TTL);
            if (!Boolean.TRUE.equals(fresh)) {
                return false;
            }
        }
        catch (Exception ex) {
            // Losing the marker can duplicate an informational message after a master
            // change, which is cheaper than never reporting a batch at all.
            log.warn("Failed to write the batch-report marker for {}", date, ex);
        }
        return true;
    }

    private String tableDesc(EtlTable table)
    {
        String db = table.getSourceDb() == null ? "" : table.getSourceDb();
        String name = table.getSourceTable() == null ? "" : table.getSourceTable();
        String full = db.isEmpty() ? name : (name.isEmpty() ? db : db + "." + name);
        return full.isEmpty() ? "任务(" + table.getId() + ")" : full + "(" + table.getId() + ")";
    }
}
