package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.SysDictRepo;
import com.wgzhao.addax.admin.repository.SysItemRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class DictService
{
    @Autowired
    private SysDictRepo sysDictRepo;

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

    public String getBizDate() {
        LocalTime localTime = LocalTime.now();
        String curDate;
        if (localTime.isAfter(getSwitchTimeAsTime())) {
             curDate = LocalDate.now().plusDays(1).format(sdf);
        } else {
             curDate = LocalDate.now().format(sdf);
        }
        String res = sysItemRepo.findLastBizDate(1021, curDate);
        return res == null ? curDate : res;
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
}
