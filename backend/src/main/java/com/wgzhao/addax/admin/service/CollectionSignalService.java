package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.alert.AlertHttpClient;
import com.wgzhao.addax.admin.alert.AlertUrlGuard;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Map;

/**
 * Optional "collection complete" webhook: when a collection finishes, notify a downstream system
 * that this business date's ODS data is ready.
 *
 * <p>Enabled only when {@code collection.complete.webhook.url} (or the
 * {@code COLLECTION_COMPLETE_WEBHOOK_URL} environment variable) is set. Best-effort: a signal
 * failure is logged and never affects the collection outcome.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollectionSignalService
{
    private static final String WEBHOOK_URL_PROP = "collection.complete.webhook.url";
    private static final String ALLOW_PRIVATE_PROP = "collection.complete.webhook.allow-private-network";
    private static final int TIMEOUT_MS = 5000;

    private final Environment env;
    private final AlertHttpClient httpClient;
    private final AlertUrlGuard urlGuard;
    private final ObjectMapper objectMapper;
    private final VwEtlTableWithSourceRepo tableViewRepo;

    public void signalComplete(long tid, LocalDate bizDate, long readRecords, long errorRecords)
    {
        String url = env.getProperty(WEBHOOK_URL_PROP);
        if (url == null || url.isBlank())
        {
            return;
        }
        VwEtlTableWithSource table = tableViewRepo.findById(tid).orElse(null);
        if (table == null)
        {
            return;
        }
        try
        {
            boolean allowPrivate = env.getProperty(ALLOW_PRIVATE_PROP, Boolean.class, false);
            urlGuard.validateWebhook(url, allowPrivate);
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("event", "collection_complete");
            payload.put("tid", tid);
            payload.put("bizDate", bizDate.toString());
            payload.put("readRecords", readRecords);
            payload.put("errorRecords", errorRecords);
            ObjectNode source = payload.putObject("source");
            source.put("name", table.getName());
            source.put("db", table.getSourceDb());
            source.put("table", table.getSourceTable());
            ObjectNode target = payload.putObject("target");
            target.put("name", table.getTargetName());
            target.put("db", table.getTargetDb());
            target.put("table", table.getTargetTable());

            Map<String, String> headers = Map.of("Content-Type", "application/json");
            HttpResponse<String> response = httpClient.send("POST", url,
                objectMapper.writeValueAsString(payload), headers, TIMEOUT_MS);
            if (response.statusCode() >= 200 && response.statusCode() < 300)
            {
                log.info("Collection complete signal sent for tid={} bizDate={} (HTTP {})",
                    tid, bizDate, response.statusCode());
            }
            else
            {
                log.warn("Collection complete signal failed for tid={}: HTTP {}", tid, response.statusCode());
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to send collection complete signal for tid={}: {}", tid, e.getMessage());
        }
    }
}
