package com.wgzhao.addax.admin.config

import com.wgzhao.addax.admin.service.SystemConfigService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class ConfigInitializer : ApplicationRunner {
    @Autowired
    private val systemConfigService: SystemConfigService? = null

    @Throws(Exception::class)
    override fun run(args: ApplicationArguments?) {
        systemConfigService!!.loadConfig()
        println("系统配置已加载完成")
    }
}
