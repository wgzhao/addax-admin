package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.redis.RedisLockService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 表级覆盖调度器（table-level schedule override）。
 *
 * 说明：
 * - 仅处理 etl_table.start_at 非空的表（覆盖调度），且命中当前分钟时将其入队。
 * - 不负责执行，不做并发控制；执行侧并发由 TaskQueueManagerV2Impl 的 permit 控制。
 * - 使用 Redis 短锁避免多节点重复扫描导致重复入队尝试。
 */
@Component
@Slf4j
@AllArgsConstructor
public class TableOverrideScheduler
{
    private final TableService tableService;
    private final TaskQueueManager queueManager;
    private final RedisLockService redisLockService;

    /**
     * 覆盖调度补偿窗口（分钟）。
     *
     * 例如设置为 2：则在每分钟 tick 时扫描 [now-2min, now] 这个闭区间内的 start_at。
     * 这样可以在 scheduler 抖动/重启/短暂故障时，避免漏掉某一分钟的触发。
     */
    private static int OVERRIDE_MISFIRE_WINDOW_MINUTES = 2;

    @Scheduled(cron = "0 * * * * ?")
    public void tick()
    {
        LocalTime nowMinute = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalTime from = nowMinute.minusMinutes(OVERRIDE_MISFIRE_WINDOW_MINUTES);

        final String lockKey = "collection:table-override:tick:lock";
        final Duration ttl = Duration.ofSeconds(50);
        String token = null;

        try {
            token = redisLockService.tryLock(lockKey, ttl);
            if (token == null) {
                return;
            }

            int enqueued = 0;

            // Handle midnight wrap: if from is after nowMinute, it means we crossed 00:00.
            if (from.isAfter(nowMinute)) {
                // [00:00, now]
                enqueued += enqueueRange(LocalTime.MIDNIGHT, nowMinute);
                // [from, 23:59]
                enqueued += enqueueRange(from, LocalTime.of(23, 59));
            }
            else {
                enqueued += enqueueRange(from, nowMinute);
            }

            if (enqueued > 0) {
                log.info("Override tick {} (window={}min): enqueued {} tables", nowMinute, OVERRIDE_MISFIRE_WINDOW_MINUTES, enqueued);
            }
        }
        catch (Exception e) {
            log.error("Error in TableOverrideScheduler", e);
        }
        finally {
            if (token != null) {
                boolean released = redisLockService.release(lockKey, token);
                if (!released) {
                    log.warn("Failed to release lock {} (token={})", lockKey, token);
                }
            }
        }
    }

    private int enqueueRange(LocalTime from, LocalTime to)
    {
        List<EtlTable> tables = tableService.getRunnableOverrideTasksBetween(from, to);
        if (tables == null || tables.isEmpty()) {
            return 0;
        }
        int enqueued = 0;
        for (EtlTable t : tables) {
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
