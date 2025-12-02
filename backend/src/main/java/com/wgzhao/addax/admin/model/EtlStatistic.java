package com.wgzhao.addax.admin.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "etl_statistic")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EtlStatistic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tid")
    private Long tid;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "take_secs")
    private Long takeSecs;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Column(name = "byte_speed")
    private Long byteSpeed;

    @Column(name = "rec_speed")
    private Long recSpeed;

    @Column(name = "total_recs")
    private Long totalRecs;

    @Column(name = "total_errors")
    private Long totalErrors;

    @Column(name = "run_date")
    private LocalDate runDate;
}
