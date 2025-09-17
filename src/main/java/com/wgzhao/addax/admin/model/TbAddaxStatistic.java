package com.wgzhao.addax.admin.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_addax_statistic")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TbAddaxStatistic
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tid;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private int takeSecs;
    private int totalBytes;
    private int byteSpeed;
    private int recSpeed;
    private int totalRecs;
    private int totalErrors;
    private LocalDate runDate;
}
