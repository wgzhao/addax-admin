package com.wgzhao.addax.admin.model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime startTs;
    private LocalDateTime endTs;
    private int takeSecs;
    private int totalBytes;
    private int byteSpeed;
    private int recSpeed;
    private int totalRecs;
    private int totalErrors;
    private LocalDateTime updateTs;
}
