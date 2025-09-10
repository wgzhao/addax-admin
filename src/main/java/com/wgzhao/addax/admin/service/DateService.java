package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.TbDictRepo;
import com.wgzhao.addax.admin.repository.TbDictionaryRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DateService
{
    @Autowired
    private TbDictRepo tbDictRepo;

    @Autowired
    private TbDictionaryRepo tbDictionaryRepo;

    private static final  DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyyMMdd");


    public String getLtd() {
        String curDate =  LocalDate.now().format(sdf);
        return tbDictionaryRepo.getLastBizDate(curDate);
    }

    public String getShortDate() {
        return  LocalDate.now().format(sdf);
    }

    public String getCurrDateTime() {
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return  LocalDateTime.now().format(sdf);
    }
}
