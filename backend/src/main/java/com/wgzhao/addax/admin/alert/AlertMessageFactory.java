package com.wgzhao.addax.admin.alert;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.alert.model.AlertEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AlertMessageFactory
{
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;
    private final String hostname = resolveHostname();

    public String hostname()
    {
        return hostname;
    }

    public ObjectNode payload(String title, String markdown, String text, String html,
        JsonNode data, Map<String, String> links)
    {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", title);
        payload.put("markdown", markdown);
        payload.put("text", text);
        payload.put("html", html);
        payload.set("data", data == null ? objectMapper.createObjectNode() : data);
        ObjectNode linkNode = objectMapper.createObjectNode();
        if (links != null) {
            links.forEach(linkNode::put);
        }
        payload.set("links", linkNode);
        payload.put("host", hostname);
        return payload;
    }

    public AlertMessage fromEvent(AlertEvent event)
    {
        JsonNode payload = event.getPayload();
        JsonNode data = payload == null ? objectMapper.createObjectNode() : payload.path("data");
        Map<String, String> links = new LinkedHashMap<>();
        JsonNode linkNode = payload == null ? null : payload.path("links");
        if (linkNode != null && linkNode.isObject()) {
            linkNode.properties().forEach(entry -> links.put(entry.getKey(), entry.getValue().asText()));
        }
        String title = payload == null ? event.getEventKey() : payload.path("title").asText(event.getEventKey());
        String markdown = payload == null ? title : payload.path("markdown").asText(title);
        String text = payload == null ? title : payload.path("text").asText(markdown);
        String html = payload == null ? "" : payload.path("html").asText("");
        return new AlertMessage(
            event.getEventKey(),
            event.getEventType(),
            AlertSeverity.parse(event.getSeverity(), AlertSeverity.P3),
            title,
            markdown,
            text,
            html,
            data,
            java.util.List.of(),
            event.getFirstSeen(),
            links
        );
    }

    public AlertMessage testMessage(String channelName, String message)
    {
        Instant now = Instant.now();
        String title = "Addax Admin 测试告警";
        String body = message == null || message.isBlank()
            ? "这是一条来自 Addax Admin 的测试告警。收到即表示通道配置可用。"
            : message.trim();
        String markdown = "## <font color=\"green\"> Addax Admin 测试告警</font>\n"
            + "**时间**: " + TIME_FORMAT.format(now) + "\n"
            + "**通道**: " + channelName + "\n"
            + "---------------------------------\n"
            + "**内容**: " + body;
        String text = title + "\n时间: " + TIME_FORMAT.format(now) + "\n通道: " + channelName + "\n内容: " + body;
        ObjectNode data = objectMapper.createObjectNode();
        data.put("test", true);
        data.put("channel", channelName);
        data.put("message", body);
        String html = "<h2>Addax Admin 测试告警</h2>"
            + "<p><strong>时间：</strong>" + escapeHtml(TIME_FORMAT.format(now)) + "</p>"
            + "<p><strong>通道：</strong>" + escapeHtml(channelName) + "</p>"
            + "<p><strong>内容：</strong>" + escapeHtml(body) + "</p>";
        return new AlertMessage("test", AlertEventType.SYSTEM.name(), AlertSeverity.P2, title,
            markdown, text, html, data, java.util.List.of(), now, Map.of());
    }

    public static String escapeHtml(String value)
    {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String resolveHostname()
    {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        }
        catch (Exception ex) {
            return "unknown-host";
        }
    }
}
