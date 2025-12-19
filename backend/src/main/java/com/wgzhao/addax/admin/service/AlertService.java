package com.wgzhao.addax.admin.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
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

    /**
     * 发送企业微信机器人消息
     */
    public void sendToWeComRobot(String message)
    {
        if (wechatKey == null || wechatKey.isEmpty()) {
            log.warn("企业微信机器人Key未配置，跳过发送消息");
            return;
        }
        try {
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

            String resp = restTemplate.postForObject(targetUrl, request, String.class);
            log.info("WeCom robot call response: {}", resp);
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
