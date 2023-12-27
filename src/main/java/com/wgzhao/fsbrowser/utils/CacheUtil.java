package com.wgzhao.fsbrowser.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
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
}
