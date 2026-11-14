package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一次采集对账结果：源端「本次采集范围」行数（Addax 上报的读取行数）与目标端「本次落地」
 * 行数是否一致。生命周期独立于采集定义与执行，属于可断言的历史事实。
 */
@Entity
@Table(name = "etl_reconcile")
@Data
public class EtlReconcile
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tid", nullable = false)
    private Long tid;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    @Column(name = "source_rows")
    private Long sourceRows;

    @Column(name = "target_rows")
    private Long targetRows;

    @Column(name = "diff")
    private Long diff;

    @Column(name = "tolerance", nullable = false)
    private Long tolerance = 0L;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist()
    {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
