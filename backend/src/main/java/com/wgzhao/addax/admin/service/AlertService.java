package com.wgzhao.addax.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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
            String formattedMessage = String.format("""
                ### **数据采集告警**

                ---

                **告警时间**: %s
                **告警节点**: %s
                **告警内容**: **%s**
                """, currentTime, hostname, message);
            Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", formattedMessage)
            );
            restTemplate.postForObject(webchatUrl, body, String.class);
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
