package com.wgzhao.addax.admin.service.source.es;

import tools.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.service.source.SourceAccessException;
import com.wgzhao.addax.admin.service.source.SourceRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Creates short-lived admin-side Elasticsearch clients. Addax has its own Jest client per task;
 * these clients exist only for connection tests and metadata discovery.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EsClientFactory
{
    private final ObjectMapper objectMapper;

    EsClient create(SourceRef source, EsConnectConfig config)
    {
        return new EsClient(config, source.username(), source.password(), objectMapper);
    }

    public boolean testConnection(SourceRef source, EsConnectConfig config)
    {
        try (EsClient client = create(source, config))
        {
            return client.ping();
        }
        catch (RuntimeException e)
        {
            log.warn("Elasticsearch connection test failed for source {}: {}", source.id(), e.getMessage());
            return false;
        }
    }

    public void ping(SourceRef source, EsConnectConfig config)
    {
        try (EsClient client = create(source, config))
        {
            client.ping();
        }
        catch (EsClient.EsHttpException e)
        {
            if (e.status() == 401 || e.status() == 403)
            {
                throw new SourceAccessException("Elasticsearch 认证失败: " + e.getMessage(), false, e);
            }
            throw new SourceAccessException("无法访问 Elasticsearch: " + e.getMessage(), true, e);
        }
        catch (RuntimeException e)
        {
            throw new SourceAccessException("无法连接 Elasticsearch: " + e.getMessage(), true, e);
        }
    }
}
