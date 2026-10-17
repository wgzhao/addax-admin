package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.service.CalendarService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * 表级覆盖调度器（table-level schedule override）。
 *
 * 说明：
 * - 仅处理 etl_table.start_at 非空的表（覆盖调度），且命中当前分钟时将其入队。
 * - 不负责执行，不做并发控制；执行侧并发由 TaskQueueManagerV2Impl 控制。
 * - 仅在 master 节点上注册，发生 failover 时新 master 重新注册。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TableOverrideScheduler
{
    private final TableService tableService;
    private final TaskQueueManager queueManager;
    private final MasterElectionService electionService;
    private final TaskScheduler taskScheduler;
    private final DictService dictService;
    private final CalendarService calendarService;

    /**
     * 覆盖调度补偿窗口（分钟）。
     *
     * 例如设置为 2：则在每分钟 tick 时扫描 [now-2min, now] 这个闭区间内的 start_at。
     * 这样可以在 scheduler 抖动/重启/短暂故障时，避免漏掉某一分钟的触发。
     */
    private static int OVERRIDE_MISFIRE_WINDOW_MINUTES = 2;

    private volatile ScheduledFuture<?> overrideFuture;

    @PostConstruct
    public void init()
    {
        electionService.onBecameMaster(() -> {
            log.info("Became master — registering table-override cron tick");
            register();
        });
        electionService.onLostMaster(() -> {
            log.info("Lost master — cancelling table-override cron tick");
            cancel();
        });
        if (electionService.isMaster()) {
            register();
        }
    }

    private synchronized void register()
    {
        cancel();
        overrideFuture = taskScheduler.schedule(this::tick, new CronTrigger("0 * * * * ?"));
    }

    private synchronized void cancel()
    {
        if (overrideFuture != null) {
            overrideFuture.cancel(false);
            overrideFuture = null;
        }
    }

    private void tick()
    {
        LocalDate today = LocalDate.now();
        LocalTime nowMinute = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalTime switchTime = dictService.getSwitchTimeAsTime();
        LocalTime from = nowMinute.minusMinutes(OVERRIDE_MISFIRE_WINDOW_MINUTES);
        // The minutes being scanned belong to the business period that is open now: before the switch
        // that is the previous calendar day. Judging an early-morning override against today's date
        // refuses Friday's trading-day tables at 01:30 on Saturday (they would never collect again),
        // and would let a rest day's trigger through on Monday morning.
        LocalDate businessDate = calendarService.businessPeriodDate(today, nowMinute);

        try {
            int enqueued = 0;

            // Handle midnight wrap: if from is after nowMinute, it means we crossed 00:00.
            if (from.isAfter(nowMinute)) {
                // Before 00:00 the window covered the previous calendar day. When the business date
                // rolls over at LocalTime.MIDNIGHT those minutes belong to the previous period and a
                // rescan would enqueue them under the new biz_date — a duplicate of a fire that may
                // already be pending under the old one. With any later switch time the wrapped range
                // [from..23:59] still lies inside the current business period (started yesterday at
                // switch time), so both sub-ranges are safe to rescan.
                if (!switchTime.equals(LocalTime.MIDNIGHT)) {
                    enqueued += enqueueRange(from, LocalTime.of(23, 59), today.minusDays(1));
                }
                enqueued += enqueueRange(LocalTime.MIDNIGHT, nowMinute, businessDate);
            }
            else {
                // Clip the window to the current business period: rescans must never reach back into
                // minutes that fired under the previous biz_date. After the daily switch a fire at
                // switchTime-2min would otherwise be re-enqueued under the NEW biz_date while its row
                // under the OLD one is still pending — collecting the table twice minutes apart.
                if (!nowMinute.isBefore(switchTime) && from.isBefore(switchTime)) {
                    from = switchTime;
                }
                enqueued += enqueueRange(from, nowMinute, businessDate);
            }

            if (enqueued > 0) {
                log.info("Override tick {} (window={}min): enqueued {} tables", nowMinute, OVERRIDE_MISFIRE_WINDOW_MINUTES, enqueued);
            }
        }
        catch (Exception e) {
            log.error("Error in TableOverrideScheduler tick", e);
        }
    }

    /**
     * @param date the business period the minutes in {@code [from, to]} belong to (see {@link
     *             CalendarService#businessPeriodDate}). A window that crosses midnight is scanned as two
     *             ranges and the earlier one belongs to the previous day; gating it with today's date
     *             would let a table scheduled on a rest day through.
     */
    private int enqueueRange(LocalTime from, LocalTime to, LocalDate date)
    {
        List<EtlTable> tables = tableService.getRunnableOverrideTasksBetween(from, to);
        if (tables == null || tables.isEmpty()) {
            return 0;
        }
        int enqueued = 0;
        for (EtlTable t : tables) {
            CalendarService.Verdict verdict = calendarService.evaluate(t.getSourceCalendar(), date);
            if (!verdict.due()) {
                calendarService.reportRefusal(verdict, t.getSourceCalendar(),
                    "table=" + t.getId() + ", date=" + date);
                continue;
            }
            if (queueManager.addTaskToQueue(t)) {
                enqueued++;
            }
        }
        return enqueued;
    }

    /**
     * 允许运行时调整补偿窗口（例如通过 debug/临时热修或以后接入配置中心）。
     */
    public static void setOverrideMisfireWindowMinutes(int minutes)
    {
        if (minutes < 0) {
            minutes = 0;
        }
        OVERRIDE_MISFIRE_WINDOW_MINUTES = minutes;
    }
}
