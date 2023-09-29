package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.dto.AddaxReportDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@RestController
@RequestMapping("/addax")
public class AddaxReportController {

    @Value("${addax.datasource.url}")
    private String jdbcUrl;

    @Value("${addax.datasource.username}")
    private String jdbcUser;

    @Value("${addax.datasource.password}")
    private String jdbcPassword;

    @Value("${addax.datasource.table}")
    private String jdbcTable;

    private static final Logger logger = LoggerFactory.getLogger(FsController.class);

    @PostMapping(value = "/addax/v1/jobReport", consumes = "application/json")
    public String jobReport(@RequestBody AddaxReportDto reportDto) {
        logger.info("job report: {}", reportDto);
        return saveReport(reportDto) ? "success" : "failed";
    }

    private boolean saveReport(AddaxReportDto dto) {
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
            Statement statement = connection.createStatement();

            String sql = String.format("insert into %s (jobname, start_ts, end_ts, take_secs, byte_speed, rec_speed, total_rec, total_err) values ('%s', %d, %d, %d, %d, %d, %d, %d)",
                    jdbcTable,
                    dto.getJobName(),
                    dto.getStartTimeStamp(),
                    dto.getEndTimeStamp(),
                    dto.getTotalCosts(),
                    dto.getByteSpeedPerSecond(),
                    dto.getRecordSpeedPerSecond(),
                    dto.getTotalReadRecords(),
                    dto.getTotalErrorRecords()
            );
            return statement.execute(sql);

        } catch (Exception e) {
            logger.error("save report error: {}", e.getMessage());
            return false;
        }
    }
}
