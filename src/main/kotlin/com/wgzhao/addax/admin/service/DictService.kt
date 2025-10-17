package com.wgzhao.addax.admin.service

import cn.hutool.json.JSONObject
import cn.hutool.json.JSONUtil
import com.wgzhao.addax.admin.model.SysDict
import com.wgzhao.addax.admin.model.SysItem
import com.wgzhao.addax.admin.model.SysItemPK
import com.wgzhao.addax.admin.repository.SysDictRepo
import com.wgzhao.addax.admin.repository.SysItemRepo
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.reflect.KClass

/**
 * 字典服务类，负责系统参数字典及字典项的相关业务操作。
 * 包含参数获取、类型转换、业务日期计算、Hive类型映射、HDFS配置等功能。
 */
@Service
class DictService(
    private val sysItemRepo: SysItemRepo,
    private val sysDictRepo: SysDictRepo
) {

    companion object {
        /** 默认切日时间  */
        private const val DEFAULT_SWITCH_TIME = "16:30"

        /** 日期格式化器 yyyyMMdd  */
        private val sdf = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    /**
     * 获取切日时间（如未配置则返回默认值）
     * @return 切日时间字符串，格式 HH:mm
     */
    fun getSwitchTime(): String = getItemValue(1000, "SWITCH_TIME") ?: DEFAULT_SWITCH_TIME

    /**
     * 获取切日时间（LocalTime对象）
     * @return 切日时间
     */
    fun getSwitchTimeAsTime(): LocalTime {
        val res = getSwitchTime()
        val (hour, minute) = res.split(":").map { it.toInt() }
        return LocalTime.of(hour, minute)
    }

    /**
     * 获取日志路径
     * @return 日志路径
     */
    fun getLogPath(): String = getItemValue(1000, "RUN_LOG") ?: (System.getProperty("user.dir") + "/logs")

    /**
     * 获得上个业务日期，规则如下：
     * 以切日为界限，如果当前时间在切日时间之前，则业务日期不早于当前时间的最近业务日期，否则为当前时间的业务日期
     * 跨日采集时也做特殊处理
     * @return 业务日期，格式为 yyyyMMdd
     */
    fun getBizDate(): String {
        val localTime = LocalTime.now()
        val switchTimeAsTime = getSwitchTimeAsTime()
        val curDate = if (localTime.isAfter(switchTimeAsTime) && localTime.isBefore(LocalTime.of(23, 59))) {
            LocalDate.now().plusDays(1).format(sdf)
        } else {
            LocalDate.now().format(sdf)
        }
        sysItemRepo.findFirstByDictCodeAndItemKeyOrderByItemKeyDesc(1021, curDate)?.let {
            return it.itemValue
        }
        return curDate
    }

    /**
     * 获取 Hive CLI 命令
     * @return Hive CLI 命令字符串
     */
    fun getHiveCli(): String = getItemValue(1000, "HIVE_CLI") ?: "hive"

    /**
     * 获取 HiveServer2 连接配置
     * @return HiveServer2 连接配置键值对
     */
    fun getHiveServer2(): Map<String, String>? {
        return getItemValue(1000, "HIVE_SERVER2")?.let { it ->
            JSONUtil.parseObj(it).associate { it.key to it.value.toString() }
        }
    }
    /**
     * 获取 Addax 安装路径
     * @return Addax 路径
     */
    fun getAddaxHome(): String = getItemValue(1000, "ADDAX") ?: "/opt/app/addax"

    /**
     * 获取字典项的值（便捷重载，默认返回字符串）
     */
    fun getItemValue(dictCode: Int, itemKey: String): String? = getItemValue(dictCode, itemKey, String::class)

    /**
     * 获取字典项的值并自动类型转换
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @param clazz 返回类型
     * @return 字典项值
     */
    fun <T : Any> getItemValue(dictCode: Int, itemKey: String, clazz: KClass<T>): T? {
        val item = sysItemRepo.findById(SysItemPK(dictCode, itemKey)).orElse(null) ?: return null
        val value: String = item.itemValue
        return when (clazz) {
            Int::class -> value.toInt() as T
            Long::class -> value.toLong() as T
            Boolean::class -> value.toBoolean() as T
            String::class -> value as T
            else -> clazz.java.cast(value)
        }
    }

    /**
     * 查询所有参数字典
     * @return 字典列表
     */
    fun findAllDicts(): List<SysDict> = sysDictRepo.findAll().filterNotNull()

    /**
     * 根据编码查询字典
     * @param dictCode 字典编码
     * @return 字典对象
     */
    fun findDictById(dictCode: Int): Optional<SysDict> = Optional.ofNullable(sysDictRepo.findById(dictCode).orElse(null))

    /**
     * 保存字典对象
     * @param dict 字典对象
     * @return 保存后的字典对象
     */
    fun saveDict(dict: SysDict): SysDict = sysDictRepo.save(dict)

    /**
     * 删除字典
     * @param dictCode 字典编码
     */
    fun deleteDict(dictCode: Int) = sysDictRepo.deleteById(dictCode)

    /**
     * 检查字典是否存在
     * @param dictCode 字典编码
     * @return 是否存在
     */
    fun existsDict(dictCode: Int): Boolean = sysDictRepo.existsById(dictCode)

    /**
     * 根据编码查询字典对象
     * @param code 字典编码
     * @return 字典对象
     */
    fun findDictByCode(code: Int): SysDict? = sysDictRepo.findByCode(code)

    /**
     * 查询指定字典下的所有字典项
     * @param dictCode 字典编码
     * @return 字典项列表
     */
    fun findItemsByDictCode(dictCode: Int): List<SysItem> = sysItemRepo.findByDictCode(dictCode)

    /**
     * 根据主键查询字典项
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @return 字典项对象
     */
    fun findItemById(dictCode: Int, itemKey: String): Optional<SysItem> = Optional.ofNullable(sysItemRepo.findById(SysItemPK(dictCode, itemKey)).orElse(null))

    /**
     * 保存字典项对象
     * @param item 字典项对象
     * @return 保存后的字典项对象
     */
    fun saveItem(item: SysItem): SysItem = sysItemRepo.save(item)

    /**
     * 删除字典项
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     */
    fun deleteItem(dictCode: Int, itemKey: String) = sysItemRepo.deleteById(SysItemPK(dictCode, itemKey))

    /**
     * 检查字典项是否存在
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @return 是否存在
     */
    fun existsItem(dictCode: Int, itemKey: String): Boolean = sysItemRepo.existsById(SysItemPK(dictCode, itemKey))

    fun getHdfsPrefix(): String = getItemValue(1000, "HDFS_PREFIX") ?: "/ods"

    fun getHiveTypeMapping(): Map<String, String> {
        return sysItemRepo.findByDictCode(2011)
            .associate { it.itemKey to it.itemValue }
    }

    fun getReaderTemplate(kind: String): String {
        return getItemValue(5001, "r$kind") ?: throw RuntimeException("没有获得 r$kind 的预设模板，检查系统配置表")
    }

    fun getWriterTemplate(kind: String): String {
        return getItemValue(5001, "w$kind") ?: throw RuntimeException("没有获得 w$kind 的预设模板，检查系统配置表")
    }

    fun getAddaxJobTemplate(kind: String): String {
        return getItemValue(5001, kind) ?: throw RuntimeException("没有获得 $kind 的预设模板，检查系统配置表")
    }
}
