package com.wgzhao.addax.admin.log;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Streams based LogSink implementation using StringRedisTemplate.opsForStream
 */
@Service
public class RedisLogSink implements LogSink {
    private final StringRedisTemplate redisTemplate;
    private final StreamOperations<String, ?, ?> streamOps;

    public RedisLogSink(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.streamOps = redisTemplate.opsForStream();
    }

    @Override
    public String pushLine(String streamKey, long seq, String line) {
        try {
            Map<String, String> map = new HashMap<>();
            map.put("seq", String.valueOf(seq));
            map.put("ts", String.valueOf(Instant.now().toEpochMilli()));
            map.put("line", line);
            // use MapRecord to add to stream
            @SuppressWarnings("unchecked")
            MapRecord<String, String, String> record = (MapRecord<String, String, String>) MapRecord.create(streamKey, map);
            RecordId id = ((StreamOperations<String, String, String>) streamOps).add(record);
            return id == null ? null : id.getValue();
        }
        catch (Exception e) {
            // best-effort, avoid throwing to not break running tasks
            return null;
        }
    }

    @Override
    public void pushEnd(String streamKey, long seq, int exitCode) {
        try {
            Map<String, String> map = new HashMap<>();
            map.put("seq", String.valueOf(seq));
            map.put("ts", String.valueOf(Instant.now().toEpochMilli()));
            map.put("eof", "1");
            map.put("exitCode", String.valueOf(exitCode));
            @SuppressWarnings("unchecked")
            MapRecord<String, String, String> record = (MapRecord<String, String, String>) MapRecord.create(streamKey, map);
            ((StreamOperations<String, String, String>) streamOps).add(record);
        }
        catch (Exception ignored) {
        }
    }
}

