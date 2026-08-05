package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alert Service
 * support send alert message to WeChat group bot, SMS, Email
 *
 * 采集告警采用"故障-恢复"状态机(类似 Zabbix/Nagios hard-state):
 * - 仅当所有重试尝试全部失败(最后一次尝试失败)时才发送告警,并记录告警状态;
 * - 告警状态存 Redis(addax:alert:state:{tid}),跨节点共享(失败与恢复可能发生在不同 worker 上);
 * - 后续采集成功时,若该表处于告警状态,发送恢复通知并清除状态。
 */
@Service
@Slf4j
public class AlertService
{

    // Scheduler used to perform non-blocking delayed retries
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // 采集告警状态 key 前缀,value 为 JSON: {alertedAt, error}
    private static final String ALERT_STATE_KEY_PREFIX = "addax:alert:state:";
    // 告警状态 TTL,兜底清理:表被停用/删除后不会再有采集成功,防止状态永久残留
    private static final Duration ALERT_STATE_TTL = Duration.ofDays(7);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${alert.wechat.url}")
    private String webchatUrl;
    @Value("${alert.wechat.key}")
    private String wechatKey;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 上报一次采集失败。
     * 仅当本次为最后一次尝试(finalAttempt)时才发送告警并记录告警状态;
     * 之后采集成功时由 {@link #reportCollectionSuccess} 发送恢复通知。
     *
     * @param finalAttempt 是否为最后一次尝试(队列场景: attempts >= maxAttempts;手动触发恒为 true)
     */
    public void reportCollectionFailure(long tid, String sourceDb, String sourceTable, String error,
                                        int attempt, int maxAttempts, boolean finalAttempt)
    {
        if (!finalAttempt) {
            log.debug("采集任务 {} 第 {}/{} 次尝试失败,非最终失败,暂不告警: {}", tid, attempt, maxAttempts, error);
            return;
        }
        String taskDesc = describeTask(tid, sourceDb, sourceTable);
        String message = attempt > 1
            ? String.format("采集任务 %s 连续 %d 次尝试均失败: %s", taskDesc, attempt, error)
            : String.format("采集任务 %s 执行失败: %s", taskDesc, error);
        // 先记录告警状态再发送,避免发送失败(网络等)导致状态丢失
        try {
            String state = objectMapper.writeValueAsString(Map.of(
                "alertedAt", LocalDateTime.now().format(TIME_FORMATTER),
                "error", error == null ? "" : error
            ));
            stringRedisTemplate.opsForValue().set(ALERT_STATE_KEY_PREFIX + tid, state, ALERT_STATE_TTL);
        }
        catch (Exception e) {
            log.warn("记录采集告警状态失败 tid={}", tid, e);
        }
        sendToWeComRobot(message);
    }

    /**
     * 上报一次采集成功。若该表正处于告警状态,发送恢复通知并清除状态。
     */
    public void reportCollectionSuccess(long tid, String sourceDb, String sourceTable)
    {
        String key = ALERT_STATE_KEY_PREFIX + tid;
        String state = null;
        try {
            state = stringRedisTemplate.opsForValue().get(key);
            if (state != null) {
                stringRedisTemplate.delete(key);
            }
        }
        catch (Exception e) {
            log.warn("读取采集告警状态失败 tid={}", tid, e);
        }
        if (state == null) {
            return;
        }
        String alertedAt = null;
        String error = null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> stateMap = objectMapper.readValue(state, Map.class);
            alertedAt = stateMap.get("alertedAt");
            error = stateMap.get("error");
        }
        catch (Exception e) {
            log.warn("解析采集告警状态失败 tid={}, state={}", tid, state, e);
        }
        String taskDesc = describeTask(tid, sourceDb, sourceTable);
        StringBuilder content = new StringBuilder("采集任务 ").append(taskDesc).append(" 已恢复正常,采集成功");
        if (alertedAt != null) {
            content.append("\n**原告警时间**: ").append(alertedAt);
        }
        if (error != null && !error.isBlank()) {
            content.append("\n**原失败原因**: ").append(error);
        }
        sendToWeComRobot("【数据采集恢复】", "green", "恢复时间", content.toString());
    }

    private String describeTask(long tid, String sourceDb, String sourceTable)
    {
        return sourceDb == null ? String.format("任务(%d)", tid) : String.format("%s.%s(%d)", sourceDb, sourceTable, tid);
    }

    /**
     * 发送企业微信机器人消息(告警)
     */
    public void sendToWeComRobot(String message)
    {
        sendToWeComRobot("【数据采集告警】", "red", "告警时间", message);
    }

    private void sendToWeComRobot(String title, String color, String timeLabel, String message)
    {
        if (wechatKey == null || wechatKey.isEmpty()) {
            log.warn("企业微信机器人Key未配置，跳过发送消息");
            return;
        }
        String currentTime = LocalDateTime.now().format(TIME_FORMATTER);
        String hostname = getHostname();
        String formattedMessage = "## <font color=\"" + color + "\"> " + title + "</font>\n" +
            "**" + timeLabel + "**: " + currentTime + "\n" +
            "**告警主机**: " + hostname + "\n" +
            "---------------------------------\n" +
            "**告警内容**: " + message;
        Map<String, Object> body = Map.of(
            "msgtype", "markdown",
            "markdown", Map.of("content", formattedMessage)
        );
        // Append key to webhook URL if not already present
        String targetUrl = webchatUrl;
        if (!targetUrl.contains("key=")) {
            String encodedKey = URLEncoder.encode(wechatKey == null ? "" : wechatKey, StandardCharsets.UTF_8);
            if (targetUrl.contains("?")) {
                targetUrl = targetUrl + "&key=" + encodedKey;
            }
            else {
                targetUrl = targetUrl + "?key=" + encodedKey;
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        // make a final copy of targetUrl so it can be referenced from the inner Runnable
        final String finalTargetUrl = targetUrl;
        // perform non-blocking retries using scheduler when WeCom rate limit (errcode == 45009) occurs
        final int maxRetries = 3;
        final AtomicInteger attempt = new AtomicInteger(0);
        final long initialDelayMs = 2000L; // initial backoff
        final long[] delayMs = new long[] {initialDelayMs};
        try {
            Runnable task = new Runnable()
            {
                @Override
                public void run()
                {
                    int curAttempt = attempt.incrementAndGet();
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> respMap = restTemplate.postForObject(finalTargetUrl, request, Map.class);
                        log.info("WeCom robot call response (attempt {}): {}", curAttempt, respMap);

                        if (respMap == null) {
                            log.warn("WeCom robot response is null on attempt {}", curAttempt);
                            return;
                        }

                        int errcode = Integer.parseInt(respMap.getOrDefault("errcode", "0").toString());
                        if (errcode == 0) {
                            // success
                            return;
                        }

                        if (errcode == 45009) {
                            if (curAttempt < maxRetries) {
                                log.warn("WeCom API rate limit (errcode 45009). attempt {}/{}. scheduling retry after {}ms", curAttempt, maxRetries, delayMs[0]);
                                long delayForThis = delayMs[0];
                                delayMs[0] = delayMs[0] * 2; // exponential backoff for next time
                                scheduler.schedule(this, delayForThis, TimeUnit.MILLISECONDS);
                            }
                            else {
                                log.warn("WeCom API rate limit and reached max retries ({}).", maxRetries);
                            }
                        }
                        else {
                            log.warn("WeCom API returned errcode {}: {}", errcode, respMap.get("errmsg"));
                        }
                    }
                    catch (Exception e) {
                        log.error("发送企业微信消息尝试失败（异步）", e);
                    }
                }
            };

            // schedule first run immediately (non-blocking)
            scheduler.execute(task);
        }
        catch (Exception e) {
            log.error("发送企业微信消息失败", e);
        }
    }

    private String getHostname()
    {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e) {
            log.error("获取主机名失败", e);
            return "未知主机";
        }
    }
}
