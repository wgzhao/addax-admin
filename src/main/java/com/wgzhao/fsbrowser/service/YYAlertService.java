package com.wgzhao.fsbrowser.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

@EnableScheduling
@EnableAsync
@ConditionalOnProperty(name = "alert.enabled")
@Service
public class YYAlertService {

    private static final Logger logger = Logger.getLogger(YYAlertService.class.getName());

    private static final int INIT_DELAY = 5 * 1000;

    // alert database connect info
    @Value("${alert.jdbc.url}")
    private String jdbcUrl;

    @Value("${alert.jdbc.user}")
    private String jdbcUser;

    @Value("${alert.jdbc.password}")
    private String jdbcPassword;

    @Value("${alert.jdbc.table}")
    private String jdbcTable;

    @Value("${alert.batchSize}")
    private int batchSize;

    @Value("${alert.wechat.url}")
    private String webchatUrl;

    @Value("${alert.wechat.key}")
    private String wechatKey;

    @Value("${alert.wechat.enabled}")
    private boolean wechatEnabled;

    @Value("${alert.sms.enabled}")
    private boolean smsEnabled;

    @Value("${alert.email.enabled}")
    private boolean emailEnabled;

    /**
     * send message to WeChat group bot
     * here is the official document: <a href="https://work.weixin.qq.com/api/doc/90000/90136/91770">群机器人配置说明</a>
     */
    @Async
    @Scheduled(fixedDelayString = "${alert.interval}", initialDelay = INIT_DELAY)
    public void sendWechatMessage() {
        if (! wechatEnabled) {
            return;
        }
        String url = String.format("%s?key=%s", webchatUrl, wechatKey);
        logger.info("send wechat message to " + url);
        String query = String.format("select MID, MSG, BKK from %s where BKK = 'Y' order by dw_clt_date asc", jdbcTable);
        ResultSet resultSet;
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
            Statement statement = connection.createStatement();
            statement.setFetchSize(batchSize);
            resultSet = statement.executeQuery(query);

            RestTemplate restTemplate = new RestTemplate();
            while (resultSet.next()) {
                String mid = resultSet.getString("MID");
                String msg = resultSet.getString("MSG");
                // payload json format
                String payload = String.format("{\"msgtype\": \"text\", \"text\": {\"content\": \"%s\"}}", msg);
                ResponseEntity<String> response = restTemplate.postForEntity(url, payload, String.class);
                if (response.getStatusCodeValue() == 200) {
                    // update database, set BKK to 'y'
                    String updateSql = String.format("update %s set BKK = 'y' where MID = '%s'", jdbcTable, mid);
                    connection.createStatement().execute(updateSql);
                }
            }
            statement.close();
        } catch (SQLException e) {
            logger.warning("failed to send wechat message: " + e);
            return;
        }
    }

    @Async
    @ConditionalOnProperty(value = {"alert.sms.enabled", "alert.enabled"})
    @Scheduled(fixedDelayString = "${alert.interval}", initialDelay = INIT_DELAY)
    public void sendSmsMessage() {
        if (! smsEnabled) {
            return;
        }
        logger.info("send sms message");
    }

    @Async
    @Scheduled(fixedDelayString = "${alert.interval}", initialDelay = INIT_DELAY)
    public void sendEmailMessage() {
        if (! emailEnabled) {
            return;
        }
        logger.info("send email message");

    }
}
