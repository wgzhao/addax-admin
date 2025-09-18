package com.wgzhao.addax.admin.service;

import lombok.extern.slf4j.Slf4j;
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

/**
 * Alert Service
 * support send alert message to WeChat group bot, SMS, Email
 */
@EnableScheduling
@EnableAsync
@Service
@Slf4j
public class AlertService
{

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
        log.info("send wechat message to {}",  url);
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
                    log.warn("send wechat message failed: {}" , response.getBody());
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
                    log.info("send wechat message success: {}  in current loop finish the current loop because of api limited",  cntSuccess);
                    statement.close();
                    connection.close();
                    return;
                }
            } // end while
            log.info("send wechat message success: {} in current loop", cntSuccess);
            statement.close();
            connection.close();
        } catch (SQLException e) {
            log.warn("failed to send wechat message" , e);
        }
    }

    @Async
    @ConditionalOnProperty(value = {"alert.sms.enabled", "alert.enabled"})
    @Scheduled(fixedDelayString = "${alert.interval}", initialDelay = INIT_DELAY)
    public void sendSmsMessage() {
        if (!smsEnabled) {
            return;
        }
        log.info("send sms message");
    }

    @Async
    @Scheduled(fixedDelayString = "${alert.interval}", initialDelay = INIT_DELAY)
    public void sendEmailMessage() {
        if (!emailEnabled) {
            return;
        }
        log.info("send email message");
    }

    /** 发送企业微信机器人消息 */
    public String sendToWecomRobot(String message) {
        return null;
//        try {
//            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
//            String hostname = getHostname();
//            String formattedMessage = String.format("""
//                ### **数据采集告警**
//
//                ---
//
//                **告警时间**: %s
//                **告警节点**: %s
//                **告警内容**: **%s**
//                """, currentTime, hostname, message);
//            Map<String, Object> body = Map.of(
//                "msgtype", "markdown",
//                "markdown", Map.of("content", formattedMessage)
//            );
//            return restTemplate.postForObject(webhookUrl, body, String.class);
//        } catch (Exception e) {
//            log.error("发送企业微信消息失败", e);
//            return "发送失败: " + e.getMessage();
//        }
    }
}
