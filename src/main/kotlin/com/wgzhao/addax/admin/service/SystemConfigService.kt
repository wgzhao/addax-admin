package com.wgzhao.addax.admin.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
class SystemConfigService {
    @Autowired
    private val dictService: DictService? = null

    private val configCache: MutableMap<String?, Any?> = HashMap<String?, Any?>()

    fun loadConfig() {
        val curDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        configCache.put("BIZ_DATE", dictService!!.getBizDate())
        configCache.put("CUR_DATETIME", curDateTime)

        configCache.put("LOG_PATH", dictService.getLogPath())

        // 切日时间
        configCache.put("SWITCH_TIME", dictService.getSwitchTime())

        // hive
        configCache.put("HIVE_CLI", dictService.getHiveCli())

        configCache.put("HDFS_PREFIX", dictService.getHdfsPrefix())
    }

    val bizDate: String?
        get() = configCache.get("BIZ_DATE") as String?

    val logPath: String?
        get() = configCache.get("LOG_PATH") as String?

    val curDateTime: String?
        get() = configCache.get("CUR_DATETIME") as String?

    val switchTime: String
        get() = configCache.get("SWITCH_TIME") as String

    val switchTimeAsTime: LocalTime
        get() {
            val switchTime = this.switchTime
            return LocalTime.parse(switchTime, DateTimeFormatter.ofPattern("HH:mm"))
        }

    val hDFSPrefix: String?
        get() = configCache.get("HDFS_PREFIX") as String?
}
