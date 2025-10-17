package com.wgzhao.addax.admin.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Map

/**
 * Alert Service
 * support send alert message to WeChat group bot, SMS, Email
 */
@Service
class AlertService(private val restTemplate: RestTemplate) {

    private val log = KotlinLogging.logger {}

    private val webchatUrl: String = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send"

    private val wechatKey: String? = null

    /**
     * 发送企业微信机器人消息
     */
    fun sendToWeComRobot(message: String?) {
        if (wechatKey == null || wechatKey.isEmpty()) {
            log.warn { "企业微信机器人Key未配置，跳过发送消息" }
            return
        }
        if (webchatUrl.isEmpty()) {
            log.warn { "企业微信机器人URL未配置，跳过发送消息" }
            return
        }
        try {
            val currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val hostname = this.hostname
            val formattedMessage = String.format(
                """
                ### **数据采集告警**

                ---

                **告警时间**: %s
                **告警节点**: %s
                **告警内容**: **%s**
                
                """.trimIndent(), currentTime, hostname, message
            )
            val body = Map.of<String?, Any?>(
                "msgtype", "markdown",
                "markdown", Map.of<String?, String?>("content", formattedMessage)
            )
            restTemplate.postForObject<String?>(webchatUrl, body, String::class)
        } catch (e: Exception) {
            log.error(e) { "发送企业微信消息失败" }
        }
    }

    private val hostname: String?
        get() {
            try {
                return InetAddress.getLocalHost().hostName
            } catch (e: Exception) {
                log.error(e) { "获取主机名失败" }
                return "未知主机"
            }
        }
}
