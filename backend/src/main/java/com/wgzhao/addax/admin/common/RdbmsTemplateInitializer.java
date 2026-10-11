package com.wgzhao.addax.admin.common;

import com.wgzhao.addax.admin.service.DictService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ensure the built-in RDBMS job/writer templates exist as dict rows on startup, so the Java constants
 * stay the single source of truth and existing deployments get them without a manual migration.
 *
 * The seed only fills a missing row: once a dict row exists it wins over the Java constant, so changing
 * a default template also requires shipping an update for the existing dict row (or deleting it).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RdbmsTemplateInitializer
{
    private final DictService dictService;

    @PostConstruct
    public void init()
    {
        try
        {
            dictService.ensureRdbmsTemplates();
        }
        catch (Exception e)
        {
            log.warn("Failed to initialize RDBMS templates", e);
        }
    }
}
