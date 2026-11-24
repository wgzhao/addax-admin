package com.wgzhao.addax.admin.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Raw query parameters of {@code GET /tables}.
 *
 * <p>Comma-separated lists stay as strings and are parsed by the controller: binding a
 * {@code List<String>} property from a model attribute relies on a conversion-service detail and
 * fails silently (one element "a,b") when that detail changes.</p>
 */
@Getter
@Setter
public class TableListRequest
{
    private int page = 0;
    private int pageSize = 10;
    private String q;
    private String statuses;
    /**
     * Legacy single-source parameter, still emitted by old bookmarks and deep links.
     */
    private String sourceId;
    private String sourceIds;
    private String dbTypes;
    private Boolean sourceEnabled;
    private String targetIds;
    private String targetTypes;
    private String targetDbContains;
    private Boolean targetEnabled;
    private String writeModes;
    private String schemaModes;
    /**
     * Last-run duration bounds in seconds; both inclusive.
     */
    private Integer durationFrom;
    private Integer durationTo;
    private String tblCommentContains;
    private LocalDate endTimeFrom;
    private LocalDate endTimeTo;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private LocalDate updatedFrom;
    private LocalDate updatedTo;
    private String scope;
    /**
     * Legacy alias of {@link #scope}.
     */
    private String lifecycle;
    private String sortField;
    private String sortOrder;
}
