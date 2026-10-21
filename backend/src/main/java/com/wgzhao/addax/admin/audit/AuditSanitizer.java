package com.wgzhao.addax.admin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AuditSanitizer
{
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern INLINE_SECRET = Pattern.compile(
        "(?i)([\"']?(?:password|passwd|pwd|secret|token|access[_-]?key|private[_-]?key)[\"']?\\s*[:=]\\s*)"
            + "(\"[^\"]*\"|'[^']*'|[^&,\\s}]+)"
    );
    private static final Pattern URL_USERINFO = Pattern.compile("://([^:/@]+):([^@/]+)@");

    private final ObjectMapper objectMapper;

    public JsonNode sanitize(Object value)
    {
        if (value == null) {
            return null;
        }
        try {
            return redact(objectMapper.valueToTree(value), null);
        }
        catch (Exception ex) {
            return JsonNodeFactory.instance.textNode(redactText(String.valueOf(value)));
        }
    }

    public JsonNode changedFields(JsonNode before, JsonNode after)
    {
        ArrayNode fields = JsonNodeFactory.instance.arrayNode();
        if (before == null || after == null || !before.isObject() || !after.isObject()) {
            return fields;
        }
        List<String> names = new ArrayList<>();
        before.fieldNames().forEachRemaining(names::add);
        after.fieldNames().forEachRemaining(name -> {
            if (!names.contains(name)) {
                names.add(name);
            }
        });
        names.stream()
            .filter(name -> !before.path(name).equals(after.path(name)))
            .sorted()
            .forEach(fields::add);
        return fields;
    }

    public String digest(String value)
    {
        if (value == null) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        }
        catch (Exception ex) {
            return null;
        }
    }

    private JsonNode redact(JsonNode node, String fieldName)
    {
        if (node == null) {
            return null;
        }
        if (isSensitiveKey(fieldName)) {
            return JsonNodeFactory.instance.textNode(REDACTED);
        }
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(entry ->
                result.set(entry.getKey(), redact(entry.getValue(), entry.getKey())));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> result.add(redact(item, fieldName)));
            return result;
        }
        if (node.isTextual()) {
            String text = node.asText();
            String trimmed = text.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    return redact(objectMapper.readTree(trimmed), fieldName);
                }
                catch (Exception ignored) {
                    // Plain text can legitimately start with a brace; fall back to string redaction.
                }
            }
            return JsonNodeFactory.instance.textNode(redactText(text));
        }
        return node;
    }

    private boolean isSensitiveKey(String key)
    {
        if (key == null) {
            return false;
        }
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.equals("pass")
            || normalized.equals("pwd")
            || normalized.equals("token")
            || normalized.endsWith("password")
            || normalized.endsWith("secret")
            || normalized.endsWith("credential")
            || normalized.endsWith("privatekey")
            || normalized.endsWith("accesskey")
            || normalized.equals("authorization")
            || normalized.equals("cookie");
    }

    private String redactText(String text)
    {
        if (text == null) {
            return null;
        }
        String result = INLINE_SECRET.matcher(text).replaceAll("$1" + REDACTED);
        return URL_USERINFO.matcher(result).replaceAll("://$1:" + REDACTED + "@");
    }
}
