package com.wgzhao.addax.admin.alert.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.alert.AlertChannelProvider;
import com.wgzhao.addax.admin.alert.AlertChannelType;
import com.wgzhao.addax.admin.alert.AlertDeliveryResult;
import com.wgzhao.addax.admin.alert.AlertHttpClient;
import com.wgzhao.addax.admin.alert.AlertMessage;
import com.wgzhao.addax.admin.alert.AlertUrlGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FeishuAlertProvider
    implements AlertChannelProvider
{
    private static final int TIMEOUT_MS = 5000;

    private final ObjectMapper objectMapper;
    private final AlertHttpClient httpClient;
    private final AlertUrlGuard urlGuard;

    @Override
    public AlertChannelType type()
    {
        return AlertChannelType.FEISHU;
    }

    @Override
    public void validate(JsonNode config)
    {
        urlGuard.validate(text(config, "webhookUrl"), type(), false);
    }

    @Override
    public AlertDeliveryResult send(JsonNode config, AlertMessage message)
    {
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            String secret = config.path("secret").asText("");
            if (!secret.isBlank()) {
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                body.put("timestamp", timestamp);
                body.put("sign", sign(timestamp, secret));
            }
            body.put("msg_type", "text");
            body.put("content", Map.of("text", message.title() + "\n" + message.text()));
            HttpResponse<String> response = httpClient.send("POST", text(config, "webhookUrl"),
                objectMapper.writeValueAsString(body), Map.of("Content-Type", "application/json"), TIMEOUT_MS);
            long latency = System.currentTimeMillis() - started;
            JsonNode json = parse(response.body());
            int code = json.has("code") ? json.path("code").asInt(-1) : json.path("StatusCode").asInt(-1);
            if (response.statusCode() / 100 == 2 && code == 0) {
                return AlertDeliveryResult.ok(response.statusCode(), WeComAlertProvider.truncate(response.body()), latency);
            }
            String reason = json.path("msg").asText(json.path("StatusMessage").asText("飞书返回错误"));
            return AlertDeliveryResult.failed(response.statusCode(), WeComAlertProvider.truncate(response.body()),
                latency, "code=" + code + " " + reason);
        }
        catch (Exception ex) {
            return AlertDeliveryResult.failed(null, null, System.currentTimeMillis() - started, ex.getMessage());
        }
    }

    private String sign(String timestamp, String secret)
        throws Exception
    {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
    }

    private JsonNode parse(String body)
    {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        }
        catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode config, String field)
    {
        String value = config.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("飞书通道缺少 " + field);
        }
        return value;
    }
}
