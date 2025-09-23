package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlColumn;
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

    //  获取采集表的源表字段，使用逗号分隔
    public String getSourceAggColumns(long tid) {
        return etlColumnRepo.getAllColumns(tid);
    }

    public List<EtlColumn> getColumns(long tid) {
        return etlColumnRepo.findAllByTidOrderByColumnId(tid);
    }
}
