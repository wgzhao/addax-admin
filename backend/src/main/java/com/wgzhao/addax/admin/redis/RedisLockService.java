package com.wgzhao.addax.admin.redis;

import com.wgzhao.addax.admin.common.Constants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
public class RedisLockService
{
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
        Long.class
    );
    private static final DefaultRedisScript<Long> EXTEND_SCRIPT = new DefaultRedisScript<>(
        // ARGV[2] is milliseconds TTL
        "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('pexpire',KEYS[1],ARGV[2]) else return 0 end",
        Long.class
    );
    // Lua for tryAcquirePermit: if scard(key) < limit then sadd(key, token); pexpire(key, ttl) end
    private static final DefaultRedisScript<Long> TRY_ACQUIRE_PERMIT_SCRIPT = new DefaultRedisScript<>(
        "local cnt = redis.call('scard', KEYS[1])\nif tonumber(cnt) < tonumber(ARGV[1]) then redis.call('sadd', KEYS[1], ARGV[2]); redis.call('pexpire', KEYS[1], ARGV[3]); return 1 else return 0 end",
        Long.class
    );
    // Lua for releasePermit: srem(key, token); if scard==0 then del key end
    private static final DefaultRedisScript<Long> RELEASE_PERMIT_SCRIPT = new DefaultRedisScript<>(
        "local removed = redis.call('srem', KEYS[1], ARGV[1]); local cnt = redis.call('scard', KEYS[1]); if cnt == 0 then redis.call('del', KEYS[1]) end; return removed;",
        Long.class
    );
    // Lua for extendPermit: if sismember(key, token) == 1 then pexpire(key, ttl) end
    private static final DefaultRedisScript<Long> EXTEND_PERMIT_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('sismember',KEYS[1],ARGV[1]) == 1 then return redis.call('pexpire',KEYS[1],ARGV[2]) else return 0 end",
        Long.class
    );
    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Try to acquire a lock. Returns a token string when acquired, or null when not acquired.
     */
    public String tryLock(String key, Duration ttl)
    {
        String token = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        if (Boolean.TRUE.equals(ok)) {
            return token;
        }
        return null;
    }

    /**
     * Release a lock previously acquired with tryLock. Returns true if released.
     */
    public boolean release(String key, String token)
    {
        try {
            Long res = redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), token);
            return res != null && res > 0L;
        }
        catch (Exception e) {
            // best-effort; log at caller
            return false;
        }
    }

    /**
     * Extend the TTL of a lock only if token matches. Returns true if TTL was updated.
     */
    public boolean extend(String key, String token, Duration ttl)
    {
        try {
            long millis = ttl.toMillis();
            Long res = redisTemplate.execute(EXTEND_SCRIPT, Collections.singletonList(key), token, String.valueOf(millis));
            return res != null && res > 0L;
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Check whether the given key exists in Redis (a simple lock presence check).
     */
    public boolean isLocked(String key)
    {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return exists;
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Try to acquire a permit from a key-backed set acting as a semaphore.
     * Returns a token when acquired, null otherwise.
     */
    public String tryAcquirePermit(String key, int limit, Duration ttl)
    {
        String token = UUID.randomUUID().toString();
        try {
            long millis = ttl.toMillis();
            Long res = redisTemplate.execute(TRY_ACQUIRE_PERMIT_SCRIPT, Collections.singletonList(key), String.valueOf(limit), token, String.valueOf(millis));
            if (res != null && res > 0L) {
                return token;
            }
            return null;
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * Release a permit token.
     */
    public boolean releasePermit(String key, String token)
    {
        try {
            Long res = redisTemplate.execute(RELEASE_PERMIT_SCRIPT, Collections.singletonList(key), token);
            return res != null && res > 0L;
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Extend permit key TTL if token is still a member.
     */
    public boolean extendPermit(String key, String token, Duration ttl)
    {
        try {
            long millis = ttl.toMillis();
            Long res = redisTemplate.execute(EXTEND_PERMIT_SCRIPT, Collections.singletonList(key), token, String.valueOf(millis));
            return res != null && res > 0L;
        }
        catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshInProgress()
    {
        return isLocked(Constants.SCHEMA_REFRESH_LOCK_KEY);
    }
}
