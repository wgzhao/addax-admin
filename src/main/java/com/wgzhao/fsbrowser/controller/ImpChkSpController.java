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
import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping("/impChkSp")
public class ImpChkSpController {

    @Autowired
    private ImpChkSpRepo impChkSpRepo;

    private String calcTradeDate(Integer shiftDay) {
        LocalDate startingDate = LocalDate.now();
        // calc 5 work day ago
        LocalDate fifthWorkday = startingDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY))  // Move to next Monday if necessary
                .plusDays(shiftDay);  // Add 5 workdays (excluding weekends)
        DateTimeFormatter sf = DateTimeFormatter.ofPattern("yyyyMMdd");
        String l5td = fifthWorkday.format(sf);
        return l5td;
    }
    // SP 计算的有效性检测结果
    @GetMapping("/validChkSp")
    public List<TbImpChkSpEntity> getValidChkSp() {
        return impChkSpRepo.findValidChkSp(calcTradeDate(5));
    }

    // SP计算的记录数检测结果
    @GetMapping("/validSpCnt")
    public List<Map<String, Object>> getValidSpCnt() {
        return impChkSpRepo.findValidSpCnt(calcTradeDate(5));
    }
}
