package com.wgzhao.addax.admin.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

@Service
public class CacheUtil {
    @Resource
    private RedisTemplate<String, Serializable> serializableRedisTemplate;

    public void set(String key, Serializable value)
    {
        serializableRedisTemplate.opsForValue().set(key, value);
    }

    public String get(String key)
    {
        return Objects.requireNonNull(serializableRedisTemplate.opsForValue().get(key)).toString();
    }

    public Long add(String key, Serializable... value)
    {
        return serializableRedisTemplate.opsForSet().add(key, value);
    }

    public Long sAdd(String key, Serializable values) {
        return serializableRedisTemplate.execute((RedisConnection connection) ->
                connection.sAdd(key.getBytes(), toByteArray(values.toString()))
        );
    }

    private byte[][] toByteArray(String... strs) {
        byte[][] result = new byte[strs.length][];
        for (int i = 0; i < strs.length; i++) {
            result[i] = strs[i].getBytes();
        }
        return result;
    }

    public void del(String key) {
        serializableRedisTemplate.delete(key);
    }

    public boolean tryLock(String key, String requestId, long timeoutSeconds) {
        Boolean success = serializableRedisTemplate.opsForValue().setIfAbsent(key, requestId, Duration.ofSeconds(timeoutSeconds));

        return Boolean.TRUE.equals(success);
    }

    public void unlock(String key, String requestId) {
        boolean hasKey  =  serializableRedisTemplate.hasKey(key);
        if (hasKey) {
            serializableRedisTemplate.delete(key);
        }
    }
}
