package com.wgzhao.addax.admin.service.source.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.service.source.SourceAccessException;
import com.wgzhao.addax.admin.service.source.SourceRef;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Creates short-lived admin-side MongoDB clients. Addax has its own client per task; these clients
 * exist only for connection tests and metadata discovery.
 */
@Component
@Slf4j
public class MongoClientFactory
{
    private static final int SERVER_SELECTION_TIMEOUT_SECONDS = 5;
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 30;

    public MongoClient create(SourceRef source, MongoConnectConfig config)
    {
        MongoClientSettings.Builder builder;
        try
        {
            builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(config.clientConnectionString()))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(
                    SERVER_SELECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .applyToSocketSettings(b -> b
                    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        catch (IllegalArgumentException e)
        {
            throw new ApiException(400, "MongoDB 连接配置无效: " + e.getMessage());
        }
        if (source.username() != null && !source.username().isBlank()
            && source.password() != null && !source.password().isBlank())
        {
            builder.credential(MongoCredential.createCredential(
                source.username(), config.authDb(), source.password().toCharArray()));
        }
        return MongoClients.create(builder.build());
    }

    public boolean testConnection(SourceRef source, MongoConnectConfig config)
    {
        try (MongoClient client = create(source, config))
        {
            client.getDatabase(config.database()).runCommand(new Document("ping", 1));
            return true;
        }
        catch (RuntimeException e)
        {
            log.warn("MongoDB connection test failed for source {}: {}", source.id(), e.getMessage());
            return false;
        }
    }

    public void ping(SourceRef source, MongoConnectConfig config)
    {
        try (MongoClient client = create(source, config))
        {
            client.getDatabase(config.database()).runCommand(new Document("ping", 1));
        }
        catch (RuntimeException e)
        {
            throw new SourceAccessException("无法连接 MongoDB: " + e.getMessage(), true, e);
        }
    }
}
