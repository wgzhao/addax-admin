package com.wgzhao.addax.admin.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Formula;

/**
 * 映射 vw_etl_table_with_source 视图
 */
@Entity
@Table(name = "vw_etl_table_with_source")
@Data
public class VwEtlTableWithSource {
    @Id
    private Long id;
    private String sourceDb;
    private String sourceTable;
    private String targetDb;
    private String targetTable;
    private String partKind;
    private String partName;
    private String filter;
    private String status;
    private String kind;
    private String updateFlag;
    private String createFlag;
    private Integer retryCnt;
    private Integer sid;
    private Integer duration;
    private String code;
    private String name;
    private String url;
    private String username;
    private String pass;
    private String startAt;
    private Boolean enabled;
    @Formula("UPPER(concat_ws('|', source_db || '.' || source_table , target_db || '.' || target_table , part_kind , part_name , code, name))")
    private String filterColumn;
}

