package com.wgzhao.addax.admin.service.source.es;

import tools.jackson.databind.JsonNode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Minimal Elasticsearch REST client used for connection tests and metadata discovery.
 *
 * <p>It intentionally speaks raw HTTP instead of the official client: the admin must talk to the
 * same range of cluster versions the Addax Jest-based reader does, and a client library that pins a
 * major version would silently narrow that range. Addax still does the actual collection.</p>
 */
final class EsClient
    implements AutoCloseable
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient http;
    private final List<URI> endpoints;
    private final String authorization;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;

    EsClient(EsConnectConfig config, String username, String password, ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
        this.endpoints = config.endpoints().stream().map(URI::create).toList();
        this.requestTimeout = Duration.ofMillis(config.readTimeoutMs());
        String auth = null;
        if (username != null && !username.isBlank() && password != null && !password.isBlank())
        {
            auth = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        }
        this.authorization = auth;

        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NORMAL);
        if (config.insecureSkipVerify())
        {
            builder.sslContext(trustAllContext());
            SSLParameters parameters = new SSLParameters();
            parameters.setEndpointIdentificationAlgorithm("");
            builder.sslParameters(parameters);
        }
        this.http = builder.build();
    }

    boolean ping()
    {
        get("/");
        return true;
    }

    JsonNode get(String path)
    {
        return execute("GET", path, null);
    }

    JsonNode post(String path, Object body)
    {
        return execute("POST", path, body);
    }

    private JsonNode execute(String method, String path, Object body)
    {
        String payload;
        try
        {
            payload = body == null ? null
                : body instanceof String text ? text : objectMapper.writeValueAsString(body);
        }
        catch (JacksonException e)
        {
            throw new EsConnectionException("无法序列化 Elasticsearch 请求: " + e.getMessage(), e);
        }

        IOException last = null;
        for (URI endpoint : endpoints)
        {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(endpoint.resolve(path))
                .timeout(requestTimeout)
                .header("Accept", "application/json");
            if (authorization != null)
            {
                request.header("Authorization", authorization);
            }
            if (payload == null)
            {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            else
            {
                request.header("Content-Type", "application/json");
                request.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            }
            try
            {
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300)
                {
                    String content = response.body();
                    return content == null || content.isBlank()
                        ? objectMapper.createObjectNode() : objectMapper.readTree(content);
                }
                throw new EsHttpException(response.statusCode(), errorMessage(response.body()));
            }
            catch (IOException e)
            {
                // A node being down says nothing about the cluster: try the next configured endpoint.
                last = e;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new EsConnectionException("访问 Elasticsearch 被中断", e);
            }
        }
        throw new EsConnectionException("无法连接 Elasticsearch: "
            + (last == null ? "没有可用地址" : last.getMessage()), last);
    }

    private static String errorMessage(String body)
    {
        if (body == null || body.isBlank())
        {
            return "Elasticsearch 返回了空错误响应";
        }
        try
        {
            JsonNode root = MAPPER.readTree(body);
            JsonNode error = root.path("error");
            if (error.isObject())
            {
                String type = error.path("type").asText("");
                String reason = error.path("reason").asText("");
                JsonNode causes = error.path("root_cause");
                if (causes.isArray() && !causes.isEmpty())
                {
                    String causeType = causes.get(0).path("type").asText("");
                    String causeReason = causes.get(0).path("reason").asText("");
                    if (!causeReason.isBlank())
                    {
                        return (causeType.isBlank() ? type : causeType) + ": "
                            + (causeReason.equals(reason) ? reason : reason + " (" + causeReason + ")");
                    }
                }
                return type.isBlank() ? reason : type + ": " + reason;
            }
            if (error.isTextual())
            {
                return error.asText();
            }
            return body.length() > 500 ? body.substring(0, 500) : body;
        }
        catch (JacksonException e)
        {
            return body.length() > 500 ? body.substring(0, 500) : body;
        }
    }

    static String encodePath(String value)
    {
        if (value == null)
        {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%2A", "*")
            .replace("%2C", ",");
    }

    private static SSLContext trustAllContext()
    {
        try
        {
            TrustManager[] managers = {new X509TrustManager()
            {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers()
                {
                    return new X509Certificate[0];
                }
            }};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, managers, new SecureRandom());
            return context;
        }
        catch (GeneralSecurityException e)
        {
            throw new EsConnectionException("无法初始化 TLS 配置: " + e.getMessage(), e);
        }
    }

    @Override
    public void close()
    {
        http.close();
    }

    static final class EsHttpException
        extends RuntimeException
    {
        private final int status;

        EsHttpException(int status, String message)
        {
            super("HTTP " + status + ": " + message);
            this.status = status;
        }

        int status()
        {
            return status;
        }
    }

    static final class EsConnectionException
        extends RuntimeException
    {
        EsConnectionException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }
}
