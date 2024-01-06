package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.dto.AddaxReportDto;
import com.wgzhao.fsbrowser.model.pg.TbAddaxSta;
import com.wgzhao.fsbrowser.repository.pg.AddaxStaRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/addax")
@CrossOrigin
public class AddaxReportController {

    private static final Logger logger = LoggerFactory.getLogger(FsController.class);

    @Autowired
    private AddaxStaRepo addaxStaRepo;

    @PostMapping(value = "/addax/v1/jobReport", consumes = "application/json")
    public TbAddaxSta jobReport(@RequestBody AddaxReportDto dto) {
        logger.info("job report: {}", dto);
        TbAddaxSta sta = new TbAddaxSta();
        sta.setJobname(dto.getJobName());
        sta.setStartTs(dto.getStartTimeStamp());
        sta.setEndTs(dto.getEndTimeStamp());
        sta.setTakeSecs(dto.getTotalCosts());
        sta.setByteSpeed(dto.getByteSpeedPerSecond());
        sta.setRecSpeed(dto.getRecordSpeedPerSecond());
        sta.setTotalRec(dto.getTotalReadRecords());
        sta.setTotalErr(dto.getTotalErrorRecords());
        sta.setUpdtDate(new java.sql.Timestamp(System.currentTimeMillis()));

        return addaxStaRepo.save(sta);
    }
}
