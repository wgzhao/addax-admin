package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.TbDictionaryPK;
import com.wgzhao.addax.admin.repository.TbDictRepo;
import com.wgzhao.addax.admin.repository.TbDictionaryRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class DictService
{
    @Autowired
    private TbDictRepo tbDictRepo;

    @Autowired
    private TbDictionaryRepo tbDictionaryRepo;

    private static final String DEFAULT_SWITCH_TIME = "16:30";
    private static final DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String getSwitchTime() {
        String res = tbDictionaryRepo.findEntryValue(1000, "SWITCH_TIME");
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
        String res = tbDictionaryRepo.findEntryValue(1062, "runlog");
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
        String res = tbDictionaryRepo.findLastBizDate(1021, curDate);
        return res == null ? curDate : res;
    }

    public String getAddaxHome() {
        String res = tbDictionaryRepo.findEntryValue(1062, "addax");
        return res == null ? "/opt/app/addax" : res;
    }

}
