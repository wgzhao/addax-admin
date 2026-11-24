package com.wgzhao.addax.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * Response shape of {@code /tables}.
 *
 * <p>The view joins the source row, so serializing it directly published the source account, password
 * and connect config with every table row. Those are operational reads (ADR-0008); this DTO
 * deliberately has no field for them. The source URL is metadata and stays available. The DTO also
 * stops view/formula columns from leaking by accident.</p>
 */
@Data
public class TableView
{
    private Long id;
    private String sourceDb;
    private String sourceTable;
    private String targetDb;
    private String targetTable;
    private JsonNode targetOptions;
    private SchemaManagementMode schemaManagementMode;
    private String targetSchemaFingerprint;
    private String filter;
    private String status;
    private Integer retryCnt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Integer sid;
    private Integer duration;
    private String name;
    private String url;
    private String dbType;
    private String sourceStartAt;
    private String calendar;
    private String startAt;
    private Boolean enabled;
    private String tblComment;
    private Integer maxConcurrency;
    private String splitPk;
    private Boolean autoPk;
    private String writeMode;
    private Long targetId;
    private JsonNode readerPluginConfig;
    private JsonNode writerPluginConfig;
    private String targetType;
    private String targetWriterTemplateKey;
    private String targetCode;
    private String targetName;
    private Boolean targetEnabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime retiredAt;
    private String retiredBy;

    public static TableView from(VwEtlTableWithSource table)
    {
        TableView view = new TableView();
        BeanUtils.copyProperties(table, view);
        return view;
    }
}
