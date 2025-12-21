package com.wgzhao.addax.admin.model;

import com.wgzhao.addax.admin.common.DbType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "etl_source")
@Setter
@Getter
@Data
public class EtlSource
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "code", length = 10, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "url", length = 500, nullable = false)
    private String url;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "pass", length = 64)
    private String pass;

    @Column(name = "start_at")
    private LocalTime startAt;

    @Column(name = "prerequisite", length = 4000)
    private String prerequisite;

    @Column(name = "pre_script", length = 4000)
    private String preScript;

    @Column(name = "remark", length = 2000)
    private String remark;

    @Column(name = "enabled")
    private boolean enabled;

    @Column(name = "max_concurrency")
    private Integer maxConcurrency = 5;

    @Column(name = "db_type", length = 50)
    private String dbType = DbType.RDBMS.getValue();
}
