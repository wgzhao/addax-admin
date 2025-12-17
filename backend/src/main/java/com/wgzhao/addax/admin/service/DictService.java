package com.wgzhao.addax.admin.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.model.SysDict;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.repository.SysDictRepo;
import com.wgzhao.addax.admin.repository.SysItemRepo;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.wgzhao.addax.admin.common.Constants.DEFAULT_SWITCH_TIME;
import static com.wgzhao.addax.admin.common.Constants.shortSdf;

/**
 * 字典服务类，负责系统参数字典及字典项的相关业务操作。
 * 包含参数获取、类型转换、业务日期计算、Hive类型映射、HDFS配置等功能。
 */
@Service
@AllArgsConstructor
public class DictService
{
    private final SysItemRepo sysItemRepo;
    private final SysDictRepo sysDictRepo;

    /**
     * 获取切日时间（如未配置则返回默认值）
     * @return 切日时间字符串，格式 HH:mm
     */
    public String getSwitchTime() {
        String res = getItemValue(1000, "SWITCH_TIME", String.class);
        return res == null ? DEFAULT_SWITCH_TIME : res;
    }

    /**
     * 获取切日时间（LocalTime对象）
     * @return 切日时间
     */
    public LocalTime getSwitchTimeAsTime() {
        String res = getSwitchTime();
        String[] split = res.split(":");
        int hour = Integer.parseInt(split[0]);
        int minute = Integer.parseInt(split[1]);
        return LocalTime.of(hour, minute);
    }

    /**
     * 获取日志路径
     * @return 日志路径
     */
    public String getLogPath() {
        String res = getItemValue(1000, "RUN_LOG", String.class);
        return res == null ? System.getProperty("user.dir") + "/logs" : res;
    }

    /**
     * 获得上个业务日期，规则如下：
     * 以切日为界限，如果当前时间在切日时间之前，则业务日期不早于当前时间的最近业务日期，否则为当前时间的业务日期
     * 跨日采集时也做特殊处理
     * @return 业务日期，格式为 yyyyMMdd
     */
    public String getBizDate() {
        LocalTime localTime = LocalTime.now();
        String curDate;
        if (localTime.isAfter(getSwitchTimeAsTime()) && localTime.isBefore(LocalTime.of(23, 59))) {
             curDate = LocalDate.now().plusDays(1).format(shortSdf);
        } else {
             curDate = LocalDate.now().format(shortSdf);
        }
        String res = sysItemRepo.findLastBizDateList(curDate);
        return res == null ? curDate : res;
    }

    /**
     * 获取 Hive CLI 命令
     * @return Hive CLI 命令字符串
     */
    public String getHiveCli() {
        String res = getItemValue(1000, "HIVE_CLI", String.class);
        return res == null ? "hive" : res;
    }

    /**
     * 获取 Addax 安装路径
     * @return Addax 路径
     */
    public String getAddaxHome() {
        String res = getItemValue(1000, "ADDAX", String.class);
        return res == null ? "/opt/app/addax" : res;
    }

    /**
     * 获取并发限制参数
     * @return 并发限制
     */
    public int getConcurrentLimit() {
        Integer res = getItemValue(1000, "CONCURRENT_LIMIT", Integer.class);
        return res == null ? 5 : res;
    }

    /**
     * 获取队列大小参数
     * @return 队列大小
     */
    public int getQueueSize() {
        Integer  res = getItemValue(1000, "QUEUE_SIZE", Integer.class);
        return res == null ? 100 : res;
    }

    public String getRdbms2HdfsJobTemplate() {
        return getItemValue(5000, "R2H", String.class);
    }

    // 读取 RDBMS Reader 模板
    public String getRdbmsReaderTemplate() {
        return getItemValue(5001, "rR", String.class);
    }

    // 获取 HDFS Writer 模板
    public String getHdfsWriterTemplate() {
        return getItemValue(5001, "wH", String.class);
    }

    /**
     * 获取 Addax 任务模板内容
     * @param kind 模板类型标识
     * @return 任务模板内容
     */
    public String getAddaxJobTemplate(String kind)
    {
        return getItemValue(5000, kind, String.class);
    }


    public void saveHdfsWriteTemplate(String template) {
        Optional<SysItem> itemOpt = sysItemRepo.findByDictCodeAndItemKey(1000, "HDFS_CONFIG");
        SysItem item;
        if (itemOpt.isPresent()) {
            item = itemOpt.get();
            item.setItemValue(template);
        } else {
            item = new SysItem();
            item.setDictCode(1000);
            item.setItemKey("HDFS_CONFIG");
            item.setItemValue(template);
        }
        sysItemRepo.save(item);
    }

    /**
     * 获取字典项的值并自动类型转换
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @param clazz 返回类型
     * @return 字典项值
     */
    public <T> T getItemValue(int dictCode, String itemKey, Class<T> clazz) {
        Optional<SysItem> res = sysItemRepo.findByDictCodeAndItemKey(dictCode, itemKey);
        if (res.isEmpty()) {
            return null;
        }
        String value = res.get().getItemValue();
        if (value == null) {
            return null;
        }
        if (clazz == Integer.class) {
            return clazz.cast(Integer.parseInt(value));
        } else if (clazz == Long.class) {
            return clazz.cast(Long.parseLong(value));
        } else if (clazz == Boolean.class) {
            return clazz.cast(Boolean.parseBoolean(value));
        } else {
            // 默认 String 类型
            return clazz.cast(value);
        }
    }

    /**
     * 获取 Hive 类型映射表
     * @return Hive类型映射表
     */
    public Map<String, String> getHiveTypeMapping() {
        return sysItemRepo.getHiveTypeItems().stream()
                .collect(java.util.stream.Collectors.toMap(
                        SysItem::getItemKey,
                        SysItem::getItemValue));
    }

    /**
     * 获取 HDFS 路径前缀
     * @return HDFS 路径前缀
     */
    public String getHdfsPrefix() {
        String res = getItemValue(1000, "HDFS_PREFIX", String.class);
        return res == null ? "/ods/" : res;
    }

    /**
     * 获取 HDFS 压缩格式
     * @return 压缩格式
     */
    public String getHdfsCompress() {
        String res = getItemValue(1000, "HDFS_COMPRESS_FORMAT", String.class);
        return res == null ? "snappy" : res;
    }

    /**
     * 获取 HDFS 存储格式
     * @return 存储格式
     */
    public String getHdfsStorageFormat() {
        String res = getItemValue(1000, "HDFS_STORAGE_FORMAT", String.class);
        return res == null ? "orc" : res;
    }

    // SysDict CRUD
    /**
     * 查询所有参数字典
     * @return 字典列表
     */
    public List<SysDict> findAllDicts() {
        return sysDictRepo.findAll();
    }
    /**
     * 根据编码查询字典
     * @param dictCode 字典编码
     * @return 字典对象
     */
    public Optional<SysDict> findDictById(int dictCode) {
        return sysDictRepo.findById(dictCode);
    }

    public Optional<SysItem> findSystemItem(int dictCode, String itemKey) {
        return sysItemRepo.findByDictCodeAndItemKey(dictCode, itemKey);
    }
    /**
     * 保存字典对象
     * @param dict 字典对象
     * @return 保存后的字典对象
     */
    public SysDict saveDict(SysDict dict) {
        return sysDictRepo.save(dict);
    }
    /**
     * 删除字典
     * @param dictCode 字典编码
     */
    public void deleteDict(int dictCode) {
        sysDictRepo.deleteById(dictCode);
    }
    /**
     * 检查字典是否存在
     * @param dictCode 字典编码
     * @return 是否存在
     */
    public boolean existsDict(int dictCode) {
        return sysDictRepo.existsById(dictCode);
    }
    /**
     * 根据编码查询字典对象
     * @param code 字典编码
     * @return 字典对象
     */
    public SysDict findDictByCode(int code) {
        return sysDictRepo.findByCode(code);
    }

    // SysItem CRUD
    /**
     * 查询指定字典下的所有字典项
     * @param dictCode 字典编码
     * @return 字典项列表
     */
    public List<SysItem> findItemsByDictCode(int dictCode) {
        return sysItemRepo.findByDictCodeOrderByDictCodeAsc(dictCode);
    }
    /**
     * 根据主键查询字典项
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @return 字典项对象
     */
    public Optional<SysItem> findItemById(int dictCode, String itemKey) {
        return sysItemRepo.findById(new com.wgzhao.addax.admin.model.SysItemPK(dictCode, itemKey));
    }
    /**
     * 保存字典项对象
     * @param item 字典项对象
     * @return 保存后的字典项对象
     */
    public SysItem saveItem(SysItem item) {
        return sysItemRepo.save(item);
    }
    /**
     * 删除字典项
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     */
    public void deleteItem(int dictCode, String itemKey) {
        sysItemRepo.deleteById(new com.wgzhao.addax.admin.model.SysItemPK(dictCode, itemKey));
    }
    /**
     * 检查字典项是否存在
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @return 是否存在
     */
    public boolean existsItem(int dictCode, String itemKey) {
        return sysItemRepo.existsById(new com.wgzhao.addax.admin.model.SysItemPK(dictCode, itemKey));
    }

    public HiveConnectDto getHiveServer2() {
        String hiveServer2 = getItemValue(1000, "HIVE_SERVER2", String.class);
        JSONObject entries = JSONUtil.parseObj(hiveServer2);
        String url = entries.getOrDefault("url", "jdbc:hive2://localhost:10000/default").toString();
        String username = entries.getOrDefault("username", "hive").toString();
        String password = entries.getOrDefault("password", "").toString();
        String driverClassName = entries.getOrDefault("driverClassName", "org.apache.hive.jdbc.HiveDriver").toString();
        String driverPath = entries.getOrDefault("driverPath", "").toString();
        return new HiveConnectDto(url, username, password, driverClassName, driverPath);
    }

    public Map<String, Object> getSysConfig() {
        Map<String, Object> result =  sysItemRepo.findByDictCode(1000).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SysItem::getItemKey,
                        SysItem::getItemValue));
        // HiveServer2 是一个 json 字符串，需要转换成对象返回
        HiveConnectDto hiveServer2 = getHiveServer2();
        result.put("HIVE_SERVER2", hiveServer2);
        return result;
    }

    public void saveSysConfig(Map<String, Object> payload) {
        for(Map.Entry<String, Object> entry: payload.entrySet()) {
            findSystemItem(1000, entry.getKey()).ifPresent(item -> {
                if (item.getItemValue() != entry.getValue()) {
                    item.setItemValue(entry.getValue().toString());
                    saveItem(item);
                }
            });
        }
    }

    public Map<String, String> getHdfsDefaultFormats() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("storageFormat", getHdfsStorageFormat());
        defaults.put("compressFormat", getHdfsCompress());
        return defaults;
    }

    // 获取三个模板
    public Map<String, SysItem> getJobTemplates() {
        return sysItemRepo.findByDictCodeIn(List.of(5000, 5001)).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SysItem::getItemKey,
                        item -> item));
    }

    public void updateJobTemplates(List<SysItem> templates) {
        for (SysItem item : templates) {
            if (item.getDictCode() != 5000 && item.getDictCode() != 5001) {
                continue;
            }
            saveItem(item);
        }
    }
}
