package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.repository.SysItemRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class DictService
{

    @Autowired
    private SysItemRepo sysItemRepo;

    private static final String DEFAULT_SWITCH_TIME = "16:30";
    private static final DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String getSwitchTime() {
        String res = getItemValue(1000, "SWITCH_TIME", String.class);
        return res == null ? DEFAULT_SWITCH_TIME : res;
    }

    public LocalTime getSwitchTimeAsTime() {
        String res = getSwitchTime();
        String[] split = res.split(":");
        int hour = Integer.parseInt(split[0]);
        int minute = Integer.parseInt(split[1]);
        return LocalTime.of(hour, minute);
    }

    public String getLogPath() {
        String res = getItemValue(1062, "runlog", String.class);
        return res == null ? System.getProperty("user.dir") + "/logs" : res;
    }

    /**
     * 获得上个业务日期，规则如下：
     * 以切日为界限，如果当前时间在切日时间之前，则业务日期不早于当前时间的最近业务日期，否则为当前时间的业务日期
     * 举例说明：
     * 假定切日是 16:30，当前日期是 2025-09-23，如果是 16:30 之前采集，则读取字典表中 1021 编号小于 2025-09-23 的最大业务日期（假定为 2025-09-22)，
     * 如果是 16:30 之后，则认为业务日期为 2025-09-23
     * 这里要考虑一个跨日采集的情况，比如如果采集时间定在 03:00，那么当前时间到了 2025-09-24，但业务日期仍然是 2025-09-23
     * @return 业务日期，格式为 yyyyMMdd
     */
    public String getBizDate() {
        LocalTime localTime = LocalTime.now();
        String curDate;
        if (localTime.isAfter(getSwitchTimeAsTime()) && localTime.isBefore(LocalTime.of(23, 59))) {
             curDate = LocalDate.now().plusDays(1).format(sdf);
        } else {
             curDate = LocalDate.now().format(sdf);
        }
        String res = sysItemRepo.findLastBizDateList(curDate);
        return res == null ? curDate : res;
    }

    public String getHiveCli() {
        String res = getItemValue(1000, "HIVE_CLI", String.class);
        return res == null ? "hive" : res;
    }

    public String getAddaxHome() {
        String res = getItemValue(1062, "addax", String.class);
        return res == null ? "/opt/app/addax" : res;
    }

    public int getConcurrentLimit() {
        Integer res = getItemValue(1000, "CONCURRENT_LIMIT", Integer.class);
        return res == null ? 5 : res;
    }

    public int getQueueSize() {
        Integer  res = getItemValue(1000, "QUEUE_SIZE", Integer.class);
        return res == null ? 100 : res;
    }

    //    select entry_content from  tb_dictionary where entry_code = '5001' and entry_value = 'r" + kind + "'"
    public String getReaderTemplate(String kind) {
        return getItemValue(5001, "r" + kind, String.class);
    }


    public <T> T getItemValue(int dictCode, String itemKey, Class<T> clazz) {
      String value = sysItemRepo.findByDictCodeAndItemKey(dictCode, itemKey).getItemValue();
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

    public String getAddaxJobTemplate(String kind)
    {
        return getItemValue(5000, kind, String.class);
    }

    // 获取 Hive 类型映射
    public Map<String, String> getHiveTypeMapping() {
        return sysItemRepo.getHiveTypeItems().stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getItemKey().toUpperCase(),
                        SysItem::getItemValue));
    }

    public String getHdfsPrefix() {
        String res = getItemValue(1000, "HDFS_PREFIX", String.class);
        return res == null ? "/ods/" : res;
    }

    public String getHdfsCompress() {
        String res = getItemValue(1000, "HDFS_COMPRESS_FORMAT", String.class);
        return res == null ? "snappy" : res;
    }

    public String getHdfsStorageFormat() {
        String res = getItemValue(1000, "HDFS_STORAGE_FORMAT", String.class);
        return res == null ? "orc" : res;
    }
}
