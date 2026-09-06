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
    // Per-tid recovery-send marker: only one node may deliver a recovery notice at a time.
    // TTL is a crash fallback — if the sender dies mid-delivery the marker expires and the next
    // successful collection retries.
    private static final String ALERT_RECOVERY_INFLIGHT_KEY_PREFIX = "addax:alert:recovery:";
    private static final Duration ALERT_RECOVERY_INFLIGHT_TTL = Duration.ofSeconds(60);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Bounded-timeout client for the synchronous recovery send: the regular RestTemplate is used by
    // the async failure path and may not have short timeouts configured.
    private final RestTemplate syncRestTemplate = createSyncRestTemplate();

    private static RestTemplate createSyncRestTemplate()
    {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

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
     *
     * Delivery ordering: the alert state is only cleared AFTER the recovery message has been
     * delivered. Clearing first (the old code) permanently lost the recovery notice whenever the
     * webhook call failed — no later success ever re-sent it. A per-tid inflight marker prevents
     * two concurrent successes (duplicate executions) from double-sending.
     */
    public void reportCollectionSuccess(long tid, String sourceDb, String sourceTable)
    {
        String key = ALERT_STATE_KEY_PREFIX + tid;
        String state;
        try {
            state = stringRedisTemplate.opsForValue().get(key);
        }
        catch (Exception e) {
            log.warn("读取采集告警状态失败 tid={}", tid, e);
            return;
        }
        if (state == null) {
            return;
        }
        String inflightKey = ALERT_RECOVERY_INFLIGHT_KEY_PREFIX + tid;
        Boolean acquired;
        try {
            acquired = stringRedisTemplate.opsForValue().setIfAbsent(inflightKey, "1", ALERT_RECOVERY_INFLIGHT_TTL);
        }
        catch (Exception e) {
            log.warn("获取恢复通知发送标记失败 tid={}", tid, e);
            return;
        }
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("恢复通知发送中(另一节点),跳过 tid={}", tid);
            return;
        }
        try {
            String alertedAt = null;
            String error = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> stateMap = objectMapper.readValue(state, Map.class);
                alertedAt = stateMap.get("alertedAt");
                error = stateMap.get("error");
            }
            catch (Exception e) {
                log.warn("解析采集告警状态失败 tid={}", tid, e);
            }
            String taskDesc = describeTask(tid, sourceDb, sourceTable);
            StringBuilder content = new StringBuilder("采集任务 ").append(taskDesc).append(" 已恢复正常,采集成功");
            if (alertedAt != null) {
                content.append("\n**原告警时间**: ").append(alertedAt);
            }
            if (error != null && !error.isBlank()) {
                content.append("\n**原失败原因**: ").append(error);
            }
            boolean delivered = sendWeComRobotSync("【数据采集恢复】", "green", "恢复时间", content.toString());
            if (delivered) {
                try {
                    stringRedisTemplate.delete(key);
                    log.info("恢复通知已发送并清除告警状态 tid={}", tid);
                }
                catch (Exception e) {
                    log.warn("清除告警状态失败 tid={}", tid, e);
                }
            }
            else {
                log.error("恢复通知发送失败,告警状态保留,下次采集成功时将重试 tid={}", tid);
            }
        }
        finally {
            try {
                stringRedisTemplate.delete(inflightKey);
            }
            catch (Exception ignored) {
            }
        }
    }

    /**
     * Synchronous single-attempt WeCom delivery with bounded timeouts.
     *
     * @return true only when WeCom acknowledged the message (errcode 0)
     */
    private boolean sendWeComRobotSync(String title, String color, String timeLabel, String message)
    {
        try {
            String content = buildMarkdownContent(title, color, timeLabel, message);
            Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", content)
            );
            Map<String, Object> resp = syncRestTemplate.postForObject(resolveTargetUrl(), body, Map.class);
            if (resp == null) {
                log.warn("WeCom sync response is null");
                return false;
            }
            int errcode = Integer.parseInt(String.valueOf(resp.getOrDefault("errcode", "0")));
            if (errcode == 0) {
                return true;
            }
            log.warn("WeCom sync delivery rejected, errcode {}: {}", errcode, resp.get("errmsg"));
            return false;
        }
        catch (Exception e) {
            log.error("发送企业微信恢复消息失败(同步)", e);
            return false;
        }
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

    private String buildMarkdownContent(String title, String color, String timeLabel, String message)
    {
        String currentTime = LocalDateTime.now().format(TIME_FORMATTER);
        String hostname = getHostname();
        return "## <font color=\"" + color + "\"> " + title + "</font>\n" +
            "**" + timeLabel + "**: " + currentTime + "\n" +
            "**告警主机**: " + hostname + "\n" +
            "---------------------------------\n" +
            "**告警内容**: " + message;
    }

    /** Append the webhook key to the configured URL when it is not already embedded. */
    private String resolveTargetUrl()
    {
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
        return targetUrl;
    }

    private void sendToWeComRobot(String title, String color, String timeLabel, String message)
    {
        if (wechatKey == null || wechatKey.isEmpty()) {
            log.warn("企业微信机器人Key未配置，跳过发送消息");
            return;
        }
        String formattedMessage = buildMarkdownContent(title, color, timeLabel, message);
        Map<String, Object> body = Map.of(
            "msgtype", "markdown",
            "markdown", Map.of("content", formattedMessage)
        );
        String targetUrl = resolveTargetUrl();
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
