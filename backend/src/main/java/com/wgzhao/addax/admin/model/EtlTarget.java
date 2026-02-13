package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "etl_target")
@Data
public class EtlTarget
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "target_type", length = 30, nullable = false)
    private String targetType;

    @Column(name = "connect_config")
    private String connectConfig;

    @Column(name = "writer_template_key", length = 32)
    private String writerTemplateKey;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "remark", length = 500)
    private String remark;
}
