package com.wgzhao.addax.admin.config

import com.wgzhao.addax.admin.service.SystemConfigService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ConfigInitializer(private val systemConfigService: SystemConfigService) {

    private val log = KotlinLogging.logger {}

    @EventListener
    fun onApplicationReady(@Suppress("UNUSED_PARAMETER") event: ApplicationReadyEvent) {
        systemConfigService.loadConfig()
        log.info { "系统配置已加载完成" }
    }
}
