package com.wgzhao.addax.admin.service

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
class SystemConfigService(
    private val dictService: DictService,
) {
    private val configCache: MutableMap<String?, Any?> = HashMap<String?, Any?>()

    fun loadConfig() {
        val curDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        configCache["BIZ_DATE"] = dictService.getBizDate()
        configCache["CUR_DATETIME"] = curDateTime

        configCache["LOG_PATH"] = dictService.getLogPath()

        // 切日时间
        configCache["SWITCH_TIME"] = dictService.getSwitchTime()

        // hive
        configCache["HIVE_CLI"] = dictService.getHiveCli()

        configCache["HDFS_PREFIX"] = dictService.getHdfsPrefix()
    }

    val bizDate: String?
        get() = configCache["BIZ_DATE"] as String?

    val logPath: String?
        get() = configCache["LOG_PATH"] as String?

    val curDateTime: String?
        get() = configCache["CUR_DATETIME"] as String?

    val switchTime: String
        get() = configCache["SWITCH_TIME"] as String

    val switchTimeAsTime: LocalTime
        get() {
            val switchTime = this.switchTime
            return LocalTime.parse(switchTime, DateTimeFormatter.ofPattern("HH:mm"))
        }

    val hDFSPrefix: String?
        get() = configCache.get("HDFS_PREFIX") as String?
}
