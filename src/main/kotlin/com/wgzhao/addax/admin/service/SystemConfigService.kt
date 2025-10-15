package com.wgzhao.addax.admin.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
class SystemConfigService(
    private val dictService: DictService,
) {
    private val logger = KotlinLogging.logger {}
    private val configCache: MutableMap<String, Any?> = HashMap()

    var bizDate: String = ""
    var logPath: String = ""
    var curDateTime: String = ""
    var switchTime: String = ""
    var switchTimeAsTime: LocalTime = LocalTime.MIDNIGHT
    var hdfsPrefix: String = ""
    var hiveCli: String = ""

    fun loadConfig() {
        curDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        bizDate = dictService.getBizDate()
        logPath = dictService.getLogPath()
        switchTime = dictService.getSwitchTime()
        switchTimeAsTime = LocalTime.parse(switchTime)
        hiveCli = dictService.getHiveCli()
        hdfsPrefix = dictService.getHdfsPrefix()
        logger.info { "System config loaded: bizDate=$bizDate, logPath=$logPath, switchTime=$switchTime, hiveCli=$hiveCli, hdfsPrefix=$hdfsPrefix" }
    }
}
