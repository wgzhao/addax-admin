package com.wgzhao.addax.admin.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name = "etl_jour")
@Data
public class EtlJour implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid")
    private Long tid;

    @Column(name = "kind", length = 32)
    private String kind;

    @Column(name = "run_date", precision = 10)
    private Long runDate;

    @Column(name = "remark", length = 4000)
    private String remark;
}

