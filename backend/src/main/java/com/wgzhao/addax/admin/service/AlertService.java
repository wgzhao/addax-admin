package com.wgzhao.addax.admin.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


/**
 * Alert Service
 * support send alert message to WeChat group bot, SMS, Email
 */
@Service
@Slf4j
public class AlertService
{

    @Value("${alert.wechat.url}")
    private String webchatUrl;

    @Value("${alert.wechat.key}")
    private String wechatKey;

    @Autowired
    private RestTemplate restTemplate;

    // Scheduler used to perform non-blocking delayed retries
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * 发送企业微信机器人消息
     */
    public void sendToWeComRobot(String message)
    {
        if (wechatKey == null || wechatKey.isEmpty()) {
            log.warn("企业微信机器人Key未配置，跳过发送消息");
            return;
        }
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String hostname = getHostname();
        String formattedMessage = "## <font color=\"red\"> 【数据采集告警】</font>\n" +
            "**告警时间**: " + currentTime + "\n" +
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
            } else {
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
        final long[] delayMs = new long[] { initialDelayMs };
        try {
             Runnable task = new Runnable() {
                 @Override
                 public void run() {
                     int curAttempt = attempt.incrementAndGet();
                     try {
                         @SuppressWarnings("unchecked")
                        Map<String, Object> respMap = restTemplate.postForObject(finalTargetUrl, request, Map.class);
                        log.info("WeCom robot call response (attempt {}): {}", curAttempt, respMap);

                        if (respMap == null) {
                            log.warn("WeCom robot response is null on attempt {}", curAttempt);
                            return;
                        }

                        Object errObj = respMap.get("errcode");
                        int errcode = 0;
                        if (errObj instanceof Number) {
                            errcode = ((Number) errObj).intValue();
                        } else if (errObj instanceof String) {
                            try {
                                errcode = Integer.parseInt((String) errObj);
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        if (errcode == 0) {
                            // success
                            return;
                        }

                        String errmsg = respMap.getOrDefault("errmsg", "").toString();

                        if (errcode == 45009) {
                            if (curAttempt < maxRetries) {
                                log.warn("WeCom API rate limit (errcode 45009). attempt {}/{}. scheduling retry after {}ms", curAttempt, maxRetries, delayMs[0]);
                                long delayForThis = delayMs[0];
                                delayMs[0] = delayMs[0] * 2; // exponential backoff for next time
                                scheduler.schedule(this, delayForThis, TimeUnit.MILLISECONDS);
                            } else {
                                log.warn("WeCom API rate limit and reached max retries ({}). last errmsg: {}", maxRetries, errmsg);
                            }
                        } else {
                            log.warn("WeCom API returned errcode {}: {}", errcode, errmsg);
                        }
                    } catch (Exception e) {
                        log.error("发送企业微信消息尝试失败（异步）", e);
                    }
                }
            };

            // schedule first run immediately (non-blocking)
            scheduler.execute(task);

        } catch (Exception e) {
            log.error("发送企业微信消息失败", e);
        }
    }

    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.error("获取主机名失败", e);
            return "未知主机";
        }
    }
}
