package com.wgzhao.addax.admin.common;

import com.wgzhao.addax.admin.service.DictService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Initialize SQL_RESERVED_KEYWORDS from DB dictionary if configured.
 * Configuration property: sql.reserved.dict.code (int). If <= 0, initialization is skipped.
 */
@Component
@Slf4j
public class SqlReservedKeywordsInitializer {

    private final DictService dictService;

    public SqlReservedKeywordsInitializer(DictService dictService) {
        this.dictService = dictService;
    }

    @PostConstruct
    public void init() {
        try {
            Set<String> sqlReservedKeywords = dictService.getSqlReservedKeywords();
            if (sqlReservedKeywords == null || sqlReservedKeywords.isEmpty()) {
                log.info("No sys items found to initialize SQL reserved keywords");
                return;
            }
            // merge two sets into one
            sqlReservedKeywords.addAll(Constants.SQL_RESERVED_KEYWORDS);
            Constants.SQL_RESERVED_KEYWORDS = sqlReservedKeywords;
        } catch (Exception e) {
            log.warn("Failed to initialize SQL reserved keywords from dict code", e);
        }
    }
}

