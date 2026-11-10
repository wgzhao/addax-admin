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

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WeComAlertProvider
    implements AlertChannelProvider
{
    private static final int TIMEOUT_MS = 5000;

    private final ObjectMapper objectMapper;
    private final AlertHttpClient httpClient;
    private final AlertUrlGuard urlGuard;

    @Override
    public AlertChannelType type()
    {
        return AlertChannelType.WECOM;
    }

    @Override
    public void validate(JsonNode config)
    {
        String url = text(config, "webhookUrl");
        urlGuard.validate(url, type(), false);
        JsonNode mobiles = config.path("mentionedMobileList");
        if (!mobiles.isMissingNode() && !mobiles.isNull() && !mobiles.isArray()) {
            throw new IllegalArgumentException("mentionedMobileList 必须是数组");
        }
    }

    @Override
    public AlertDeliveryResult send(JsonNode config, AlertMessage message)
    {
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("content", message.markdown());
            boolean mentionAll = config.path("mentionAll").asBoolean(false);
            if (mentionAll) {
                markdown.put("mentioned_list", List.of("@all"));
            }
            List<String> mobiles = new ArrayList<>();
            config.path("mentionedMobileList").forEach(node -> {
                if (node.isTextual() && !node.asText().isBlank()) {
                    mobiles.add(node.asText());
                }
            });
            if (!mobiles.isEmpty()) {
                markdown.put("mentioned_mobile_list", mobiles);
            }
            Map<String, Object> body = Map.of("msgtype", "markdown", "markdown", markdown);
            HttpResponse<String> response = httpClient.send("POST", text(config, "webhookUrl"),
                objectMapper.writeValueAsString(body), Map.of("Content-Type", "application/json"), TIMEOUT_MS);
            long latency = System.currentTimeMillis() - started;
            JsonNode json = parse(response.body());
            int errcode = json.path("errcode").asInt(response.statusCode() == 200 ? 0 : -1);
            if (response.statusCode() / 100 == 2 && errcode == 0) {
                return AlertDeliveryResult.ok(response.statusCode(), truncate(response.body()), latency);
            }
            String reason = json.path("errmsg").asText("企业微信返回错误");
            return AlertDeliveryResult.failed(response.statusCode(), truncate(response.body()), latency,
                "errcode=" + errcode + " " + reason);
        }
        catch (Exception ex) {
            return AlertDeliveryResult.failed(null, null, System.currentTimeMillis() - started, ex.getMessage());
        }
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
            throw new IllegalArgumentException("企业微信通道缺少 " + field);
        }
        return value;
    }

    static String truncate(String body)
    {
        if (body == null) {
            return null;
        }
        return body.length() <= 2000 ? body : body.substring(0, 2000);
    }
}
