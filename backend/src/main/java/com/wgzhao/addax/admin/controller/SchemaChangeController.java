package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.model.SchemaChangeLog;
import com.wgzhao.addax.admin.service.SchemaChangeLogService;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Schema evolution log: which collection columns were added, dropped or retyped.
 * Read-only; records are produced by the collection schema refresh path.
 */
@RestController
@RequestMapping("/schema-changes")
@AllArgsConstructor
public class SchemaChangeController
{

    private final SchemaChangeLogService schemaChangeLogService;

    @GetMapping
    public ResponseEntity<PageResponse<SchemaChangeLog>> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(value = "changeType", required = false) String changeType,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(value = "sortField", required = false) String sortField,
        @RequestParam(value = "sortOrder", required = false) String sortOrder)
    {
        return ResponseEntity.ok(PageResponse.from(
            schemaChangeLogService.search(page, pageSize, changeType, keyword, from, to,
                sortField, sortOrder)));
    }
}
