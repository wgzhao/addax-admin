package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "etl_jour")
@Data
public class EtlJour
    implements Serializable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid")
    private Long tid;

    @Column(name = "kind", length = 32)
    private String kind;

    @Column(name = "start_at")
    @CreationTimestamp
    private LocalDateTime startAt;

    @Column(name = "status")
    private boolean status;

    @Column(name = "cmd")
    private String cmd;

    @Column(name = "duration")
    private Long duration = 0L;

    @Column(name = "error_msg", length = 4000)
    private String errorMsg;
}
