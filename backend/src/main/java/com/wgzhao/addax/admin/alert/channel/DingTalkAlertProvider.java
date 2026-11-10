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
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DingTalkAlertProvider
    implements AlertChannelProvider
{
    private static final int TIMEOUT_MS = 5000;

    private final ObjectMapper objectMapper;
    private final AlertHttpClient httpClient;
    private final AlertUrlGuard urlGuard;

    @Override
    public AlertChannelType type()
    {
        return AlertChannelType.DINGTALK;
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
            List<String> atMobiles = new ArrayList<>();
            config.path("atMobiles").forEach(node -> {
                if (node.isTextual() && !node.asText().isBlank()) {
                    atMobiles.add(node.asText());
                }
            });
            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("title", message.title());
            markdown.put("text", message.markdown());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);
            body.put("at", Map.of("atMobiles", atMobiles, "isAtAll", config.path("atAll").asBoolean(false)));
            String url = signedUrl(text(config, "webhookUrl"), config.path("secret").asText(""));
            HttpResponse<String> response = httpClient.send("POST", url,
                objectMapper.writeValueAsString(body), Map.of("Content-Type", "application/json"), TIMEOUT_MS);
            long latency = System.currentTimeMillis() - started;
            JsonNode json = parse(response.body());
            int errcode = json.path("errcode").asInt(response.statusCode() == 200 ? 0 : -1);
            if (response.statusCode() / 100 == 2 && errcode == 0) {
                return AlertDeliveryResult.ok(response.statusCode(), WeComAlertProvider.truncate(response.body()), latency);
            }
            String reason = json.path("errmsg").asText("钉钉返回错误");
            return AlertDeliveryResult.failed(response.statusCode(), WeComAlertProvider.truncate(response.body()),
                latency, "errcode=" + errcode + " " + reason);
        }
        catch (Exception ex) {
            return AlertDeliveryResult.failed(null, null, System.currentTimeMillis() - started, ex.getMessage());
        }
    }

    /**
     * DingTalk custom bots accept a timestamp + HMAC-SHA256 signature so the
     * shared secret never travels in the URL itself.
     */
    private String signedUrl(String webhookUrl, String secret)
        throws Exception
    {
        if (secret == null || secret.isBlank()) {
            return webhookUrl;
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sign = Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        String separator = webhookUrl.contains("?") ? "&" : "?";
        return webhookUrl + separator + "timestamp=" + timestamp
            + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);
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
            throw new IllegalArgumentException("钉钉通道缺少 " + field);
        }
        return value;
    }
}
