package com.wgzhao.addax.admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.Record
        ;
import org.springframework.data.redis.connection.stream.StreamListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Controller providing SSE subscription and history fetch from Redis Stream.
 * Note: SSE auth must be compatible with your frontend. This simple implementation assumes
 * cookie/session or that the client provides a token as a query parameter.
 */
@RestController
@RequestMapping("/api/logs")
public class LogStreamController {

    private final StringRedisTemplate redisTemplate;
    private final StreamOperations<String, String, String> streamOps;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "logstream-sse");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public LogStreamController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.streamOps = redisTemplate.opsForStream();
    }

    @GetMapping(path = "{taskId}/history")
    public List<Map<String, String>> history(@PathVariable String taskId,
                                             @RequestParam(name = "startId", required = false, defaultValue = "-") String startId,
                                             @RequestParam(name = "count", required = false, defaultValue = "100") int count) {
        String key = "log:task:" + taskId;
        try {
            // XRANGE key start end COUNT count   -> use '-' .. '+'; if startId provided, use startId..+
            String start = startId == null || startId.isBlank() ? "-" : startId;
            List<MapRecord<String, String, String>> entries = streamOps.range(key, start, "+", count);
            if (entries == null) return List.of();
            return entries.stream().map(r -> r.getValue()).collect(Collectors.toList());
        }
        catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping(path = "{taskId}/subscribe-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeSse(@PathVariable String taskId,
                                   @RequestParam(name = "startId", required = false, defaultValue = "$") String startId) {
        String key = "log:task:" + taskId;
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        AtomicBoolean cancelled = new AtomicBoolean(false);

        executor.submit(() -> {
            try {
                // If startId != $ then send history from startId (exclusive) to +
                String from = (startId == null || startId.isBlank()) ? "$" : startId;
                if (!"$".equals(from)) {
                    // XRANGE (startId .. +)
                    List<MapRecord<String, String, String>> entries = streamOps.range(key, from, "+", 1000);
                    if (entries != null) {
                        for (MapRecord<String, String, String> rec : entries) {
                            if (cancelled.get()) return;
                            try {
                                emitter.send(rec.getValue());
                            }
                            catch (IOException ignored) {
                                cancelled.set(true);
                                return;
                            }
                        }
                    }
                }

                // Now block-read new messages using XREAD BLOCK
                String lastId = "$".equals(from) ? null : from;
                while (!cancelled.get()) {
                    List<org.springframework.data.redis.connection.stream.StreamMessage<String, String>> msgs =
                        streamOps.read(org.springframework.data.redis.connection.StreamReadOptions.empty().block(Duration.ofSeconds(5)), org.springframework.data.redis.connection.stream.StreamOffset.create(key, lastId == null ? org.springframework.data.redis.connection.stream.ReadOffset.lastConsumed() : org.springframework.data.redis.connection.stream.ReadOffset.from(lastId)));
                    if (msgs != null) {
                        for (org.springframework.data.redis.connection.stream.StreamMessage<String, String> m : msgs) {
                            if (cancelled.get()) return;
                            try {
                                emitter.send(m.getValue());
                                lastId = m.getId().getValue();
                            }
                            catch (IOException ignored) {
                                cancelled.set(true);
                                return;
                            }
                        }
                    }
                }
            }
            catch (Exception e) {
                // best-effort ignore
            }
            finally {
                emitter.complete();
            }
        });

        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        return emitter;
    }
}

