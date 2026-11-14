package com.wgzhao.addax.admin.alert;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks credential-bearing values before they leave the server. The sentinel is
 * recognized on write, so an edit form that never saw the secret can send the
 * mask back unchanged without wiping the stored value.
 */
@Component
@RequiredArgsConstructor
public class AlertConfigMasker
{
    public static final String MASK = "******";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "webhookurl", "url", "secret", "password", "passwd", "token", "accesstoken",
        "hmacsecret", "apikey", "key", "authorization"
    );

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
        "authorization", "proxyauthorization", "cookie", "setcookie", "xapikey", "apikey",
        "xauthtoken", "xtoken", "token", "xaddaxsignature"
    );

    /**
     * Webhook URL credentials usually live in one query parameter or in the last
     * path segment. Masking only that part preserves the fixed prefix in the edit
     * form, so the operator can keep using the vendor URL without retyping it.
     */
    private static final Pattern URL_SECRET_PARAM = Pattern.compile(
        "(?i)([?&](?:key|access[_-]?token|token|secret|api[_-]?key|sign|signature|password)=)([^&#]*)");
    private static final Pattern HOOK_PATH = Pattern.compile("(?i)(/hook/)([^/?#]+)");

    private final ObjectMapper objectMapper;

    public JsonNode mask(JsonNode config)
    {
        if (config == null) {
            return objectMapper.createObjectNode();
        }
        return maskNode(config);
    }

    public JsonNode mergeMasked(JsonNode incoming, JsonNode existing)
    {
        if (incoming == null || incoming.isNull()) {
            return existing;
        }
        if (existing == null || existing.isNull()) {
            return incoming;
        }
        return mergeNode(incoming, existing);
    }

    private JsonNode maskNode(JsonNode node)
    {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode value = entry.getValue();
                if (isSensitive(name) && value.isTextual() && !value.asText().isBlank()) {
                    result.put(name, maskSecretValue(name, value.asText()));
                }
                else if ("headers".equalsIgnoreCase(name) && value.isObject()) {
                    result.set(name, maskHeaders(value));
                }
                else {
                    result.set(name, maskNode(value));
                }
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(item -> array.add(maskNode(item)));
            return array;
        }
        return node.deepCopy();
    }

    private JsonNode maskHeaders(JsonNode headers)
    {
        ObjectNode result = objectMapper.createObjectNode();
        headers.properties().forEach(entry -> {
            if (SENSITIVE_HEADERS.contains(normalize(entry.getKey())) && entry.getValue().isTextual()) {
                result.put(entry.getKey(), MASK);
            }
            else {
                result.set(entry.getKey(), maskNode(entry.getValue()));
            }
        });
        return result;
    }

    private JsonNode mergeNode(JsonNode incoming, JsonNode existing)
    {
        if (incoming.isObject() && existing.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = incoming.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode incomingValue = entry.getValue();
                JsonNode existingValue = existing.get(name);
                boolean masked = incomingValue.isTextual() && existingValue != null
                    && (MASK.equals(incomingValue.asText())
                        || (isSensitive(name) && incomingValue.asText().contains(MASK)));
                if (masked) {
                    result.set(name, existingValue.deepCopy());
                }
                else if (existingValue != null) {
                    result.set(name, mergeNode(incomingValue, existingValue));
                }
                else {
                    result.set(name, incomingValue.deepCopy());
                }
            }
            // Keep configured fields the client omitted (e.g. an older form version).
            existing.properties().forEach(entry -> {
                if (!result.has(entry.getKey())) {
                    result.set(entry.getKey(), entry.getValue().deepCopy());
                }
            });
            return result;
        }
        if (incoming.isArray() && existing.isArray()) {
            return incoming.deepCopy();
        }
        return incoming.deepCopy();
    }

    private String maskSecretValue(String name, String value)
    {
        String normalized = normalize(name);
        // The IM channels use a fixed prefix that the edit form shows separately;
        // a generic Webhook URL stays fully masked because its shape is arbitrary.
        if ("webhookurl".equals(normalized)) {
            String partiallyMasked = maskUrlSecret(value);
            if (partiallyMasked != null) {
                return partiallyMasked;
            }
        }
        return MASK;
    }

    /**
     * @return the URL with only its credential part masked, or {@code null} when
     * the URL has no recognizable credential segment and must be masked whole.
     */
    private String maskUrlSecret(String url)
    {
        Matcher param = URL_SECRET_PARAM.matcher(url);
        if (param.find()) {
            return param.replaceAll("$1" + Matcher.quoteReplacement(MASK));
        }
        Matcher hook = HOOK_PATH.matcher(url);
        if (hook.find()) {
            return hook.replaceAll("$1" + Matcher.quoteReplacement(MASK));
        }
        return null;
    }

    private boolean isSensitive(String name)
    {
        return SENSITIVE_KEYS.contains(normalize(name));
    }

    private static String normalize(String name)
    {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
