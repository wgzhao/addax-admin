package com.wgzhao.addax.admin.alert.channel;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebhookAlertProvider
    implements AlertChannelProvider
{
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final List<Integer> DEFAULT_SUCCESS_STATUS = List.of(200, 201, 202, 204);

    private final ObjectMapper objectMapper;
    private final AlertHttpClient httpClient;
    private final AlertUrlGuard urlGuard;

    @Override
    public AlertChannelType type()
    {
        return AlertChannelType.WEBHOOK;
    }

    @Override
    public void validate(JsonNode config)
    {
        String url = text(config, "url");
        boolean allowPrivate = config.path("allowPrivateNetwork").asBoolean(false);
        urlGuard.validateWebhook(url, allowPrivate);
        String method = config.path("method").asText("POST").toUpperCase();
        if (!List.of("POST", "PUT", "PATCH").contains(method)) {
            throw new IllegalArgumentException("Webhook 方法只支持 POST/PUT/PATCH");
        }
        JsonNode headers = config.path("headers");
        if (!headers.isMissingNode() && !headers.isNull()) {
            if (!headers.isObject()) {
                throw new IllegalArgumentException("headers 必须是 JSON 对象");
            }
            headers.properties().forEach(entry -> {
                if (!entry.getValue().isTextual()) {
                    throw new IllegalArgumentException("headers." + entry.getKey() + " 必须是字符串");
                }
            });
        }
        int timeout = config.path("timeoutMs").asInt(DEFAULT_TIMEOUT_MS);
        if (timeout < 1000 || timeout > 15000) {
            throw new IllegalArgumentException("timeoutMs 必须在 1000-15000 之间");
        }
        for (JsonNode status : config.path("successStatus")) {
            if (!status.isInt() || status.asInt() < 100 || status.asInt() > 599) {
                throw new IllegalArgumentException("successStatus 必须是合法的 HTTP 状态码");
            }
        }
    }

    @Override
    public AlertDeliveryResult send(JsonNode config, AlertMessage message)
    {
        long started = System.currentTimeMillis();
        try {
            boolean allowPrivate = config.path("allowPrivateNetwork").asBoolean(false);
            String url = text(config, "url");
            // Re-check DNS at send time; the save-time check alone allows rebinding.
            urlGuard.validateWebhook(url, allowPrivate);
            String method = config.path("method").asText("POST").toUpperCase();
            int timeout = config.path("timeoutMs").asInt(DEFAULT_TIMEOUT_MS);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            JsonNode headerNode = config.path("headers");
            if (headerNode.isObject()) {
                headerNode.properties().forEach(entry -> headers.put(entry.getKey(), entry.getValue().asText()));
            }
            String body = renderBody(config, message);
            String secret = config.path("hmacSecret").asText("");
            if (!secret.isBlank()) {
                headers.put("X-Addax-Signature", "sha256=" + hmacHex(body, secret));
            }
            HttpResponse<String> response = httpClient.send(method, url, body, headers, timeout);
            long latency = System.currentTimeMillis() - started;
            List<Integer> successStatus = new ArrayList<>(DEFAULT_SUCCESS_STATUS);
            if (config.path("successStatus").isArray() && config.path("successStatus").size() > 0) {
                successStatus.clear();
                config.path("successStatus").forEach(node -> successStatus.add(node.asInt()));
            }
            if (successStatus.contains(response.statusCode())) {
                return AlertDeliveryResult.ok(response.statusCode(), WeComAlertProvider.truncate(response.body()), latency);
            }
            return AlertDeliveryResult.failed(response.statusCode(), WeComAlertProvider.truncate(response.body()),
                latency, "HTTP " + response.statusCode());
        }
        catch (Exception ex) {
            return AlertDeliveryResult.failed(null, null, System.currentTimeMillis() - started, ex.getMessage());
        }
    }

    private String renderBody(JsonNode config, AlertMessage message)
        throws Exception
    {
        String template = config.path("payloadTemplate").asText("");
        if (template.isBlank()) {
            return objectMapper.writeValueAsString(defaultPayload(message));
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("eventKey", message.eventKey());
        values.put("eventType", message.eventType());
        values.put("severity", message.severity() == null ? "" : message.severity().name());
        values.put("title", message.title());
        values.put("markdown", message.markdown());
        values.put("text", message.text());
        values.put("html", message.html());
        values.put("occurredAt", message.occurredAt() == null ? "" : message.occurredAt().toString());
        values.put("data", objectMapper.writeValueAsString(message.data()));
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", escapeJsonString(entry.getValue()));
        }
        return rendered;
    }

    private ObjectNode defaultPayload(AlertMessage message)
    {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventKey", message.eventKey());
        payload.put("eventType", message.eventType());
        payload.put("severity", message.severity() == null ? "" : message.severity().name());
        payload.put("title", message.title());
        payload.put("text", message.text());
        payload.put("markdown", message.markdown());
        payload.put("occurredAt", message.occurredAt() == null ? "" : message.occurredAt().toString());
        payload.set("data", message.data());
        return payload;
    }

    private String hmacHex(String body, String secret)
        throws Exception
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return hex.toString();
    }

    static String escapeJsonString(String value)
    {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private String text(JsonNode config, String field)
    {
        String value = config.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Webhook 通道缺少 " + field);
        }
        return value;
    }
}
