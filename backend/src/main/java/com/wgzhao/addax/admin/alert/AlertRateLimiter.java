package com.wgzhao.addax.admin.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Per-channel fixed-window limiter shared by all nodes through Redis. Alert
 * storms must not get the bot rate-limited by the vendor; when the window is
 * full the delivery is postponed to the next minute instead of failed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertRateLimiter
{
    private static final long WINDOW_MS = 60_000L;
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
        "local current = redis.call('incr', KEYS[1]) "
            + "if current == 1 then redis.call('pexpire', KEYS[1], 70000) end "
            + "if current > tonumber(ARGV[1]) then return 0 else return 1 end",
        Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(long channelId, int limitPerMinute)
    {
        if (limitPerMinute <= 0) {
            return true;
        }
        String key = "addax:alert:rate:" + channelId + ":" + (System.currentTimeMillis() / WINDOW_MS);
        try {
            Long allowed = redisTemplate.execute(SCRIPT, List.of(key), String.valueOf(limitPerMinute));
            return allowed == null || allowed == 1L;
        }
        catch (Exception ex) {
            // Alerting must keep working when Redis is degraded; the vendor is the
            // last line of defence and a burst is better than silence.
            log.debug("Rate limiter unavailable for channel {}, allowing delivery: {}", channelId, ex.getMessage());
            return true;
        }
    }

    public Instant nextWindow()
    {
        long now = System.currentTimeMillis();
        return Instant.ofEpochMilli((now / WINDOW_MS + 1) * WINDOW_MS);
    }
}
