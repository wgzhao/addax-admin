package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.TbDict;
import com.wgzhao.addax.admin.repository.TbDictRepo;
import com.wgzhao.addax.admin.repository.TbDictionaryRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;

@Service
public class DateService
{
    @Autowired
    private TbDictRepo tbDictRepo;

    @Autowired
    private TbDictionaryRepo tbDictionaryRepo;

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

    public String getLtd() {
        String curDate = sdf.format(LocalDateTime.now().toLocalDate());
        return tbDictionaryRepo.getLastBizDate(curDate);
    }
}
