package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.pg.TbImpChkSpEntity;
import com.wgzhao.fsbrowser.repository.pg.ImpChkSpRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/impChkSp")
public class ImpChkSpController {

    @Autowired
    private ImpChkSpRepo impChkSpRepo;

    // SP 计算的有效性检测结果
    @GetMapping("/validChkSp")
    public List<TbImpChkSpEntity> getValidChkSp() {

        LocalDate startingDate = LocalDate.now();
        // calc 5 work day ago
        LocalDate fifthWorkday = startingDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY))  // Move to next Monday if necessary
                .plusDays(5);  // Add 4 workdays (excluding weekends)
        DateTimeFormatter sf = DateTimeFormatter.ofPattern("yyyyMMdd");
        String l5td = fifthWorkday.format(sf);
        return impChkSpRepo.findValidChkSp(l5td);
    }
}
