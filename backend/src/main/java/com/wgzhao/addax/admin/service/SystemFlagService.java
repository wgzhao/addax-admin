package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.Constants;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Deprecated: use RedisLockService instead
 * 系统标志服务：用于检查系统级别的标志位，如刷新状态
 */
@Deprecated
@Service
@AllArgsConstructor
@Slf4j
public class SystemFlagService
{
    public static final String KEY_SCHEMA_REFRESH = Constants.SCHEMA_REFRESH_LOCK_KEY;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 检查刷新是否正在进行：直接检查 Redis key
     */
    public boolean isRefreshInProgress()
    {
        try {
            Boolean exists = stringRedisTemplate.hasKey(KEY_SCHEMA_REFRESH);
            return exists;
        }
        catch (Exception e) {
            log.error("isRefreshInProgress failed (redis error)", e);
            throw e;
        }
    }
}
