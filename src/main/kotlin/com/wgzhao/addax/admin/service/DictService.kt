package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.model.SysDict
import com.wgzhao.addax.admin.model.SysItem
import com.wgzhao.addax.admin.model.SysItemPK
import com.wgzhao.addax.admin.repository.SysDictRepo
import com.wgzhao.addax.admin.repository.SysItemRepo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 字典服务类，负责系统参数字典及字典项的相关业务操作。
 * 包含参数获取、类型转换、业务日期计算、Hive类型映射、HDFS配置等功能。
 */
@Service
class DictService(
    private val sysItemRepo: SysItemRepo,
    private val sysDictRepo: SysDictRepo
) {

    val switchTime: String
        /**
         * 获取切日时间（如未配置则返回默认值）
         * @return 切日时间字符串，格式 HH:mm
         */
        get() = getItemValue(1000, "SWITCH_TIME", String::class.java) ?: DEFAULT_SWITCH_TIME

    val switchTimeAsTime: LocalTime
        /**
         * 获取切日时间（LocalTime对象）
         * @return 切日时间
         */
        get() {
            val res = switchTime
            val (hour, minute) = res.split(":").map { it.toInt() }
            return LocalTime.of(hour, minute)
        }

    val logPath: String
        /**
         * 获取日志路径
         * @return 日志路径
         */
        get() = getItemValue(1000, "RUN_LOG", String::class.java) ?: System.getProperty("user.dir") + "/logs"

    val bizDate: String
        /**
         * 获得上个业务日期，规则如下：
         * 以切日为界限，如果当前时间在切日时间之前，则业务日期不早于当前时间的最近业务日期，否则为当前时间的业务日期
         * 跨日采集时也做特殊处理
         * @return 业务日期，格式为 yyyyMMdd
         */
        get() {
            val localTime = LocalTime.now()
            val curDate = if (localTime.isAfter(switchTimeAsTime) && localTime.isBefore(LocalTime.of(23, 59))) {
                LocalDate.now().plusDays(1).format(sdf)
            } else {
                LocalDate.now().format(sdf)
            }
            return sysItemRepo.findLastBizDateList(curDate) ?: curDate
        }

    val hiveCli: String
        /**
         * 获取 Hive CLI 命令
         * @return Hive CLI 命令字符串
         */
        get() = getItemValue(1000, "HIVE_CLI", String::class.java) ?: "hive"

    val addaxHome: String
        /**
         * 获取 Addax 安装路径
         * @return Addax 路径
         */
        get() = getItemValue(1000, "ADDAX", String::class.java) ?: "/opt/app/addax"

    companion object {
        /** 默认切日时间  */
        private const val DEFAULT_SWITCH_TIME = "16:30"

        /** 日期格式化器 yyyyMMdd  */
        private val sdf = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    /**
     * 获取字典项的值并自动类型转换
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @param clazz 返回类型
     * @return 字典项值
     */
    fun <T> getItemValue(dictCode: Int, itemKey: String, clazz: Class<T?>): T? {
        val item = sysItemRepo.findById(SysItemPK(dictCode, itemKey)).orElse(null) ?: return null
        val value: String? = item.getItemValue()
        if (value == null) {
            return null
        }
        return when (clazz) {
            Int::class.java -> clazz.cast(value.toInt())
            Long::class.java -> clazz.cast(value.toLong())
            Boolean::class.java -> clazz.cast(value.toBoolean())
            else -> clazz.cast(value)
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
    fun findDictByCode(code: String): SysDict? = sysDictRepo.findByCode(code)

    /**
     * 查询指定字典下的所有字典项
     * @param dictCode 字典编码
     * @return 字典项列表
     */
    fun findItemsByDictCode(dictCode: Int): List<SysItem> = sysItemRepo.findByDictCode(dictCode).filterNotNull()

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
}
