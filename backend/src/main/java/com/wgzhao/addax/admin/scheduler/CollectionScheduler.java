package com.wgzhao.addax.admin.scheduler;

import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.redis.RedisLockService;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.service.SystemConfigService;
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
import java.util.Map;

@Component
@Slf4j
@AllArgsConstructor
public class CollectionScheduler
{
    private final EtlSourceRepo etlSourceRepo;
    private final TableService tableService;
    private final TaskQueueManager queueManager;
    private final RedisLockService redisLockService;
    private final SystemConfigService configService;

    // ---- legacy APIs kept for compatibility with source-level scheduler logic ----
    // In tick-based mode we don't register per-source timers anymore, but other services
    // still call these methods (create/update/delete source). Keep them as no-op/log.

    public void scheduleOrUpdateTask(EtlSource source)
    {
        // no-op in tick mode: per-source timers removed
        if (source != null) {
            log.info("Tick scheduler enabled, ignore scheduleOrUpdateTask for source {}", source.getCode());
        }
    }

    public void cancelTask(String code)
    {
        // no-op in tick mode
        if (code != null) {
            log.info("Tick scheduler enabled, ignore cancelTask for source {}", code);
        }
    }

    /**
     * 分钟级 tick 调度器：将命中调度点的表入队。
     *
     * 说明：
     * 1) 表级 start_at 优先生效；为空则继承 etl_source.start_at
     * 2) 只负责入队，执行端并发仍由 TaskQueueManagerV2Impl 的 permit 控制
     * 3) 通过每源限额 + pending 上限保护做削峰
     */
    @Scheduled(cron = "0 * * * * ?")
    public void tick()
    {
        // truncate to minute to have stable matching
        LocalTime nowMinute = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);

        // use a short lock to avoid multiple nodes doing the same enqueue scan
        final String lockKey = "collection:tick:lock";
        final Duration ttl = Duration.ofSeconds(50);
        String token = null;
        try {
            token = redisLockService.tryLock(lockKey, ttl);
            if (token == null) {
                return;
            }

            int pendingLimit = configService.getQueueSize();
            MapStatusGuard guard = new MapStatusGuard(queueManager.getQueueStatus());
            if (guard.queueSize() >= pendingLimit) {
                log.info("Queue already full-ish (size={}), skip enqueue this tick", guard.queueSize());
                return;
            }

            // 1) enqueue override tables whose table.startAt hits nowMinute
            int overrideEnqueued = 0;
            List<EtlTable> overrideTables = tableService.getRunnableOverrideTasksByStartAt(nowMinute);
            if (overrideTables != null && !overrideTables.isEmpty()) {
                for (EtlTable t : overrideTables) {
                    if (queueManager.addTaskToQueue(t)) {
                        overrideEnqueued++;
                    }
                }
                log.info("Tick {}: enqueued {} override tables", nowMinute, overrideEnqueued);
            }

            // 2) enqueue inherited tables for sources whose source.startAt hits nowMinute
            List<EtlSource> sources = etlSourceRepo.findByEnabled(true);
            for (EtlSource source : sources) {
                if (!source.isEnabled() || source.getStartAt() == null) {
                    continue;
                }
                if (!source.getStartAt().truncatedTo(ChronoUnit.MINUTES).equals(nowMinute)) {
                    continue;
                }

                int maxConcurrency = source.getMaxConcurrency() == null ? 5 : source.getMaxConcurrency();
                int perSourceEnqueueCap = Math.max(10, maxConcurrency * 3);

                int enqueued = 0;
                List<EtlTable> inherited = tableService.getRunnableInheritedTasksBySource(source.getId());
                if (inherited == null || inherited.isEmpty()) {
                    continue;
                }
                for (EtlTable t : inherited) {
                    if (enqueued >= perSourceEnqueueCap) {
                        break;
                    }
                    if (queueManager.addTaskToQueue(t)) {
                        enqueued++;
                    }
                }

                log.info("Tick {}: enqueued {} inherited tables for source {}", nowMinute, enqueued, source.getCode());
            }
        }
        catch (Exception e) {
            log.error("Error in tick scheduler", e);
        }
        finally {
            if (token != null) {
                redisLockService.release(lockKey, token);
            }
        }
    }

    /**
     * tiny helper to parse queue status without depending on internal keys
     */
    private record MapStatusGuard(Map<String, Object> status)
    {
        int queueSize()
        {
            if (status == null) {
                return 0;
            }
            Object v = status.get("queueSize");
            if (v instanceof Number n) {
                return n.intValue();
            }
            if (v instanceof String s) {
                try {
                    return Integer.parseInt(s);
                }
                catch (Exception ignored) {
                }
            }
            return 0;
        }
    }
}
