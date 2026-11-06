package com.wgzhao.addax.admin.service.source.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.service.source.SourceRef;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalized Elasticsearch connection settings.
 *
 * <p>The Addax reader takes a single {@code endpoint}; the admin client may be given several so a
 * metadata call can fail over when one node is down. Credentials live in {@code etl_source}, not in
 * the endpoint or the options, exactly like the MongoDB adapter.</p>
 */
public record EsConnectConfig(
    List<String> endpoints,
    Map<String, String> options
)
{
    private static final Pattern ENDPOINT = Pattern.compile(
        "^(https?)://(\\[[0-9a-fA-F:]+]|[A-Za-z0-9._-]+)(:(\\d{1,5}))?/?$");
    private static final Pattern SCROLL = Pattern.compile("^\\d+(ms|s|m|h|d)$");
    private static final Set<String> ALLOWED_OPTIONS = new LinkedHashSet<>(List.of(
        "insecureSkipVerify", "connectTimeoutMs", "readTimeoutMs",
        "scroll", "batchSize", "searchType",
        "multiThread", "compression", "discovery"));
    private static final Set<String> REJECTED_OPTIONS = Set.of(
        "username", "password", "endpoint", "url", "index", "column", "search");
    private static final int MAX_ENDPOINTS = 5;
    private static final int MAX_URL_LENGTH = 1000;

    public EsConnectConfig
    {
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        options = options == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public static EsConnectConfig fromSource(SourceRef source)
    {
        JsonNode node = source.connectConfig();
        if (node != null && node.has("endpoints"))
        {
            return fromJson(node);
        }
        return fromUrl(source.url());
    }

    public static EsConnectConfig fromUrl(String url)
    {
        if (url == null || url.isBlank())
        {
            throw new ApiException(400, "Elasticsearch 采集源需要连接地址");
        }
        List<String> endpoints = new ArrayList<>();
        for (String item : url.split(","))
        {
            String endpoint = item.trim();
            if (!endpoint.isEmpty())
            {
                endpoints.add(endpoint);
            }
        }
        return new EsConnectConfig(endpoints, Map.of());
    }

    public static EsConnectConfig fromJson(JsonNode node)
    {
        List<String> endpoints = new ArrayList<>();
        JsonNode endpointNode = node.get("endpoints");
        if (endpointNode != null && endpointNode.isArray())
        {
            endpointNode.forEach(item -> {
                if (item != null && !item.isNull())
                {
                    endpoints.add(item.asText());
                }
            });
        }
        else if (endpointNode != null && endpointNode.isTextual())
        {
            endpoints.add(endpointNode.asText());
        }
        Map<String, String> options = new LinkedHashMap<>();
        JsonNode optionsNode = node.get("options");
        if (optionsNode != null && optionsNode.isObject())
        {
            optionsNode.fields().forEachRemaining(entry ->
                options.put(entry.getKey(), entry.getValue().asText()));
        }
        return new EsConnectConfig(endpoints, options);
    }

    public void validate(String username, String password)
    {
        if (endpoints.isEmpty())
        {
            throw new ApiException(400, "Elasticsearch 至少需要一个 http(s)://host:port 地址");
        }
        if (endpoints.size() > MAX_ENDPOINTS)
        {
            throw new ApiException(400, "Elasticsearch 地址最多支持 " + MAX_ENDPOINTS + " 个");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String endpoint : endpoints)
        {
            String normalized = normalizeEndpoint(endpoint);
            if (!seen.add(normalized))
            {
                throw new ApiException(400, "Elasticsearch 地址重复: " + endpoint);
            }
        }
        if (canonicalUrl().length() > MAX_URL_LENGTH)
        {
            throw new ApiException(400, "Elasticsearch 地址总长度超过 " + MAX_URL_LENGTH + " 个字符");
        }
        for (Map.Entry<String, String> entry : options.entrySet())
        {
            String key = entry.getKey();
            if (REJECTED_OPTIONS.contains(key))
            {
                throw new ApiException(400, "Elasticsearch 连接选项 " + key + " 不能写在 options 中");
            }
            if (!ALLOWED_OPTIONS.contains(key))
            {
                throw new ApiException(400, "不支持的 Elasticsearch 连接选项: " + key);
            }
            validateOption(key, entry.getValue());
        }
        boolean hasUser = username != null && !username.isBlank();
        boolean hasPass = password != null && !password.isBlank();
        if (hasUser != hasPass)
        {
            throw new ApiException(400, "Elasticsearch 用户名和密码必须同时填写或同时留空");
        }
    }

    private static void validateOption(String key, String value)
    {
        switch (key)
        {
            case "insecureSkipVerify", "multiThread", "compression", "discovery" -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
                {
                    throw new ApiException(400, "Elasticsearch 选项 " + key + " 必须是 true 或 false");
                }
            }
            case "connectTimeoutMs" -> requireInt(key, value, 1000, 120_000);
            case "readTimeoutMs" -> requireInt(key, value, 1000, 600_000);
            case "batchSize" -> requireInt(key, value, 1, 10_000);
            case "scroll" -> {
                if (!value.isBlank() && !SCROLL.matcher(value.trim()).matches())
                {
                    throw new ApiException(400, "Elasticsearch 选项 scroll 格式无效，例如 5m 或 30s");
                }
            }
            case "searchType" -> {
                String type = value.toUpperCase(Locale.ROOT);
                if (!"QUERY_THEN_FETCH".equals(type) && !"DFS_QUERY_THEN_FETCH".equals(type))
                {
                    throw new ApiException(400, "Elasticsearch 选项 searchType 仅支持 QUERY_THEN_FETCH 或 DFS_QUERY_THEN_FETCH");
                }
            }
            default -> throw new ApiException(400, "不支持的 Elasticsearch 连接选项: " + key);
        }
    }

    private static void requireInt(String key, String value, int min, int max)
    {
        try
        {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max)
            {
                throw new ApiException(400, "Elasticsearch 选项 " + key + " 必须在 " + min + " 到 " + max + " 之间");
            }
        }
        catch (NumberFormatException e)
        {
            throw new ApiException(400, "Elasticsearch 选项 " + key + " 必须是整数");
        }
    }

    /**
     * Credential-free display URL. Options are deliberately left out: the structured
     * {@code connect_config} column is their single source of truth.
     */
    public String canonicalUrl()
    {
        List<String> normalized = new ArrayList<>(endpoints.size());
        for (String endpoint : endpoints)
        {
            normalized.add(normalizeEndpoint(endpoint));
        }
        return String.join(",", normalized);
    }

    /**
     * The Addax plugin accepts one endpoint. Failover between multiple nodes stays on the admin side;
     * the reader reaches the rest of the cluster through node discovery when that option is on.
     */
    public String readerEndpoint()
    {
        if (endpoints.isEmpty())
        {
            throw new ApiException(400, "Elasticsearch 采集源需要连接地址");
        }
        return normalizeEndpoint(endpoints.get(0));
    }

    public boolean insecureSkipVerify()
    {
        return bool("insecureSkipVerify", false);
    }

    public int connectTimeoutMs()
    {
        return integer("connectTimeoutMs", 5000);
    }

    public int readTimeoutMs()
    {
        return integer("readTimeoutMs", 60_000);
    }

    public String scroll()
    {
        String value = options.get("scroll");
        // An explicitly empty scroll disables it; only an absent option gets the default.
        return value == null ? "5m" : value.trim();
    }

    public int batchSize()
    {
        return integer("batchSize", 1000);
    }

    public String searchType()
    {
        String value = options.get("searchType");
        return value == null || value.isBlank() ? "DFS_QUERY_THEN_FETCH" : value.trim().toUpperCase(Locale.ROOT);
    }

    public boolean multiThread()
    {
        return bool("multiThread", true);
    }

    public boolean compression()
    {
        return bool("compression", true);
    }

    public boolean discovery()
    {
        return bool("discovery", false);
    }

    public int readerTimeoutSeconds()
    {
        return Math.max(1, (readTimeoutMs() + 999) / 1000);
    }

    private boolean bool(String key, boolean defaultValue)
    {
        String value = options.get(key);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private int integer(String key, int defaultValue)
    {
        String value = options.get(key);
        if (value == null || value.isBlank())
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    public JsonNode toJson()
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        var array = node.putArray("endpoints");
        endpoints.forEach(array::add);
        ObjectNode optionsNode = node.putObject("options");
        options.forEach(optionsNode::put);
        return node;
    }

    private static String normalizeEndpoint(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            throw new ApiException(400, "Elasticsearch 地址不能为空");
        }
        String endpoint = raw.trim();
        var matcher = ENDPOINT.matcher(endpoint);
        if (!matcher.matches())
        {
            throw new ApiException(400, "Elasticsearch 地址格式无效: " + raw
                + "，应为 http://host:port 或 https://host:port");
        }
        int port = matcher.group(4) == null ? -1 : Integer.parseInt(matcher.group(4));
        if (port > 65_535 || port == 0)
        {
            throw new ApiException(400, "Elasticsearch 端口无效: " + raw);
        }
        try
        {
            URI uri = new URI(endpoint);
            if (uri.getUserInfo() != null)
            {
                throw new ApiException(400, "连接地址中不能包含用户名/密码，请使用表单的账号密码字段");
            }
        }
        catch (URISyntaxException e)
        {
            throw new ApiException(400, "Elasticsearch 地址无效: " + e.getMessage());
        }
        String scheme = matcher.group(1).toLowerCase(Locale.ROOT);
        String hostPort = matcher.group(2) + (matcher.group(3) == null ? "" : matcher.group(3));
        return scheme + "://" + hostPort;
    }
}
