package com.wgzhao.fsbrowser.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
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
        if (!wechatEnabled) {
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
            restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
            int cntSuccess = 0;
            int errcode;
            JsonParser jsonParser = JsonParserFactory.getJsonParser();
            while (resultSet.next()) {
                String mid = resultSet.getString("MID");
                String msg = resultSet.getString("MSG");
                //add color for error message
                if (msg.contains("失败")) {
                    msg = "## <font color='red'>【失败告警】</font> \n" + msg;
                } else {
                    msg = "## <font color='info'>【消息通知】</font> \n" + msg;
                }
                // payload json format
                String payload = String.format("{\"msgtype\": \"markdown\", \"markdown\": {\"content\": \"%s\"}}", msg);
                ResponseEntity<String> response = restTemplate.postForEntity(url, payload, String.class);
                if (response.getStatusCode() != HttpStatusCode.valueOf(200)) {
                    logger.warning("send wechat message failed: " + response.getBody());
                    continue;
                }
                Map<String, Object> json = jsonParser.parseMap(response.getBody());
                errcode = Integer.parseInt(json.get("errcode").toString());
                if (errcode == 0) {
                    // update database, set BKK to 'y'
                    String updateSql = String.format("update %s set BKK = 'y' where MID = '%s'", jdbcTable, mid);
                    connection.createStatement().execute(updateSql);
                    cntSuccess++;
                } else {
                    // most condition is caused by api has been limited , errcode = 45009
                    // refs: https://open.work.weixin.qq.com/devtool/query?e=45009
                    // give up, the current loop game over
                    logger.info("send wechat message success: " + cntSuccess + " in current loop");
                    logger.info("finish the current loop because of api limited");
                    statement.close();
                    connection.close();
                    return;
                }
            } // end while
            logger.info("send wechat message success: " + cntSuccess + " in current loop");
            statement.close();
            connection.close();
        } catch (SQLException e) {
            logger.warning("failed to send wechat message: " + e);
        }
    }

    @Async
    @ConditionalOnProperty(value = {"alert.sms.enabled", "alert.enabled"})
    @Scheduled(fixedDelayString = "${alert.interval}", initialDelay = INIT_DELAY)
    public void sendSmsMessage() {
        if (!smsEnabled) {
            return;
        }
        logger.info("send sms message");
    }

    @Async
    @Scheduled(fixedDelayString = "${alert.interval}", initialDelay = INIT_DELAY)
    public void sendEmailMessage() {
        if (!emailEnabled) {
            return;
        }
        logger.info("send email message");

    }
}
