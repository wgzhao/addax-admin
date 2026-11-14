package com.wgzhao.addax.admin.service.source.mongo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import com.mongodb.ConnectionString;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.service.source.SourceRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalized MongoDB connection settings.
 *
 * <p>The Addax reader does not accept a connection string: it receives an address array and builds
 * {@code mongodb://host1,host2} itself. URI options are carried by appending them to the last address
 * (the resulting string is a valid MongoDB URI), which is a documented workaround rather than a clean
 * interface. {@code mongodb+srv} cannot be represented at all and is rejected.</p>
 */
public record MongoConnectConfig(
    List<String> address,
    String database,
    String authDb,
    Map<String, String> options
)
{
    private static final Pattern HOST_PORT = Pattern.compile(
        "^(\\[[0-9a-fA-F:]+]|[A-Za-z0-9._-]+)(:\\d{1,5})?$");
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
        "tls", "tlsInsecure", "tlsAllowInvalidCertificates", "tlsCAFile",
        "replicaSet", "readPreference", "directConnection", "retryWrites",
        "serverSelectionTimeoutMS", "connectTimeoutMS", "socketTimeoutMS");
    private static final Set<String> REJECTED_OPTIONS = Set.of(
        "username", "password", "authSource", "maxPoolSize");

    public MongoConnectConfig
    {
        address = address == null ? List.of() : List.copyOf(address);
        options = options == null ? Map.of() : Map.copyOf(options);
        if (authDb == null || authDb.isBlank())
        {
            authDb = database;
        }
    }

    public static MongoConnectConfig fromSource(SourceRef source)
    {
        JsonNode node = source.connectConfig();
        if (node != null && node.has("address"))
        {
            MongoConnectConfig config = fromJson(node);
            config.validate(source.username(), source.password());
            return config;
        }
        return fromUri(source.url(), source.username(), source.password());
    }

    public static MongoConnectConfig fromUri(String uri, String username, String password)
    {
        if (uri == null || uri.isBlank())
        {
            throw new ApiException(400, "MongoDB 采集源需要连接地址");
        }
        if (uri.startsWith("mongodb+srv://"))
        {
            throw new ApiException(400, "Addax mongodbreader 不支持 mongodb+srv://，请填写具体的 host:port 列表");
        }
        if (!uri.startsWith("mongodb://"))
        {
            throw new ApiException(400, "MongoDB 连接地址必须以 mongodb:// 开头");
        }
        ConnectionString parsed;
        try
        {
            parsed = new ConnectionString(uri);
        }
        catch (IllegalArgumentException e)
        {
            throw new ApiException(400, "MongoDB 连接地址无效: " + e.getMessage());
        }
        if (parsed.getCredential() != null && parsed.getCredential().getUserName() != null)
        {
            throw new ApiException(400, "连接地址中不能包含用户名/密码，请使用表单的账号密码字段");
        }
        String database = parsed.getDatabase();
        if (database == null || database.isBlank())
        {
            throw new ApiException(400, "MongoDB 连接地址必须包含数据库名，例如 mongodb://host:27017/orders");
        }
        String authDb = parsed.getCredential() != null && parsed.getCredential().getSource() != null
            ? parsed.getCredential().getSource() : database;
        MongoConnectConfig config = new MongoConnectConfig(parsed.getHosts(), database, authDb, Map.of());
        config.validate(username, password);
        return config;
    }

    public static MongoConnectConfig fromJson(JsonNode node)
    {
        List<String> addresses = new ArrayList<>();
        JsonNode addressNode = node.get("address");
        if (addressNode != null && addressNode.isArray())
        {
            addressNode.forEach(item -> {
                if (item != null && !item.isNull())
                {
                    addresses.add(item.asText());
                }
            });
        }
        else if (addressNode != null && addressNode.isTextual())
        {
            addresses.add(addressNode.asText());
        }
        String database = text(node, "database");
        String authDb = text(node, "authDb");
        Map<String, String> options = new LinkedHashMap<>();
        JsonNode optionsNode = node.get("options");
        if (optionsNode != null && optionsNode.isObject())
        {
            optionsNode.properties().forEach(entry ->
                options.put(entry.getKey(), entry.getValue().asText()));
        }
        return new MongoConnectConfig(addresses, database, authDb, options);
    }

    public void validate(String username, String password)
    {
        if (address.isEmpty())
        {
            throw new ApiException(400, "MongoDB 至少需要一个 host:port 地址");
        }
        for (String host : address)
        {
            if (host == null || host.isBlank() || !HOST_PORT.matcher(host.trim()).matches())
            {
                throw new ApiException(400, "MongoDB 地址格式无效: " + host);
            }
        }
        if (database == null || database.isBlank())
        {
            throw new ApiException(400, "MongoDB 数据库名不能为空");
        }
        if (database.length() > 63 || database.matches(".*[/\\\\.,\"$*<>:|?\\s].*"))
        {
            throw new ApiException(400, "MongoDB 数据库名无效: " + database);
        }
        if (authDb == null || authDb.isBlank() || authDb.length() > 63)
        {
            throw new ApiException(400, "MongoDB authDb 无效");
        }
        for (String key : options.keySet())
        {
            if (REJECTED_OPTIONS.contains(key))
            {
                throw new ApiException(400, "MongoDB 连接选项 " + key + " 不能写在 options 中");
            }
            if (!ALLOWED_OPTIONS.contains(key))
            {
                throw new ApiException(400, "不支持的 MongoDB 连接选项: " + key);
            }
        }
        boolean hasUser = username != null && !username.isBlank();
        boolean hasPass = password != null && !password.isBlank();
        if (hasUser != hasPass)
        {
            throw new ApiException(400, "MongoDB 用户名和密码必须同时填写或同时留空");
        }
    }

    /**
     * Credential-free URL for display and auditing. The Addax reader gets the address array instead.
     */
    public String canonicalUrl()
    {
        StringBuilder url = new StringBuilder("mongodb://").append(String.join(",", address));
        url.append('/').append(database);
        Map<String, String> query = new LinkedHashMap<>(options);
        if (authDb != null && !authDb.isBlank())
        {
            query.putIfAbsent("authSource", authDb);
        }
        if (!query.isEmpty())
        {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet())
            {
                if (!first)
                {
                    url.append('&');
                }
                url.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        return url.toString();
    }

    /**
     * Address array handed to the Addax reader. Options are appended to the last entry because the
     * plugin joins the entries into one URI before parsing it.
     */
    public List<String> readerAddresses()
    {
        if (options.isEmpty())
        {
            return address;
        }
        List<String> result = new ArrayList<>(address);
        StringBuilder last = new StringBuilder(result.get(result.size() - 1));
        last.append("/?");
        boolean first = true;
        for (Map.Entry<String, String> entry : options.entrySet())
        {
            if (!first)
            {
                last.append('&');
            }
            last.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        result.set(result.size() - 1, last.toString());
        return result;
    }

    public String clientConnectionString()
    {
        Map<String, String> query = new LinkedHashMap<>(options);
        if (authDb != null && !authDb.isBlank())
        {
            query.putIfAbsent("authSource", authDb);
        }
        StringBuilder url = new StringBuilder("mongodb://").append(String.join(",", address));
        url.append('/').append(database);
        if (!query.isEmpty())
        {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet())
            {
                if (!first)
                {
                    url.append('&');
                }
                url.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        return url.toString();
    }

    public JsonNode toJson()
    {
        ObjectMapper mapper = JsonMapper.builder().build();
        ObjectNode node = mapper.createObjectNode();
        var array = node.putArray("address");
        address.forEach(array::add);
        node.put("database", database);
        node.put("authDb", authDb);
        ObjectNode optionsNode = node.putObject("options");
        options.forEach(optionsNode::put);
        return node;
    }

    private static String text(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
