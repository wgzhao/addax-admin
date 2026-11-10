package com.wgzhao.addax.admin.alert;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class AlertHttpClient
{
    private static final String USER_AGENT = "addax-admin-alert/1.0";
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    public HttpResponse<String> send(String method, String url, String body,
        Map<String, String> headers, int timeoutMs)
        throws Exception
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(timeoutMs <= 0 ? 5000 : timeoutMs))
            .header("User-Agent", USER_AGENT);
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null) {
                    builder.header(name, value);
                }
            });
        }
        String verb = method == null || method.isBlank() ? "POST" : method.trim().toUpperCase();
        if ("GET".equals(verb) || "DELETE".equals(verb)) {
            if (body != null && !body.isBlank()) {
                builder.method(verb, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
            else {
                builder.method(verb, HttpRequest.BodyPublishers.noBody());
            }
        }
        else {
            builder.method(verb, HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
