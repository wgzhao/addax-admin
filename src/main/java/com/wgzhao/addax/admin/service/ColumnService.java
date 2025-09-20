package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 采集表字段信息管理
 */
@Service
@Slf4j
@AllArgsConstructor
public class ColumnService
{
    private final EtlColumnRepo etlColumnRepo;

    public String getSourceColumns(long tid) {
        return etlColumnRepo.getAllColumns(tid);
    }
}
