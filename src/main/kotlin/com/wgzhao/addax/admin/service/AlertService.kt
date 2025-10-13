package com.wgzhao.addax.admin.service

import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Map

/**
 * Alert Service
 * support send alert message to WeChat group bot, SMS, Email
 */
@Service
@Slf4j
class AlertService {
    @Value("\${alert.wechat.url}")
    private val webchatUrl: String? = null

    @Value("\${alert.wechat.key}")
    private val wechatKey: String? = null


    @Autowired
    private val restTemplate: RestTemplate? = null

    /**
     * 发送企业微信机器人消息
     */
    fun sendToWeComRobot(message: String?) {
        if (wechatKey == null || wechatKey.isEmpty()) {
            AlertService.log.warn("企业微信机器人Key未配置，跳过发送消息")
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
            restTemplate!!.postForObject<String?>(webchatUrl, body, String::class.java)
        } catch (e: Exception) {
            AlertService.log.error("发送企业微信消息失败", e)
        }
    }

    private val hostname: String?
        get() {
            try {
                return InetAddress.getLocalHost().getHostName()
            } catch (e: Exception) {
                AlertService.log.error("获取主机名失败", e)
                return "未知主机"
            }
        }
}
