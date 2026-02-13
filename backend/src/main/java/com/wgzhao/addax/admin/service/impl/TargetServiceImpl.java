package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.TargetService;
import com.wgzhao.addax.admin.service.target.TargetAdapterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.net.MalformedURLException;
import java.sql.Connection;

/**
 * 目标端服务门面。
 * 对外保持现有 TargetService 接口不变，内部转发到目标端适配器。
 */
@Service
@Primary
@RequiredArgsConstructor
public class TargetServiceImpl
    implements TargetService
{
    private final TargetAdapterRegistry targetAdapterRegistry;
    private final TargetServiceWithHiveImpl hiveTargetAdapter;

    @Override
    public boolean addPartition(long taskId, String db, String table, String partName, String partValue)
    {
        return targetAdapterRegistry.resolveDefault().addPartition(taskId, db, table, partName, partValue);
    }

    @Override
    public boolean createOrUpdateHiveTable(VwEtlTableWithSource etlTable)
    {
        return targetAdapterRegistry.resolve(etlTable).createOrUpdateTable(etlTable);
    }

    @Override
    public boolean prepareBeforeRun(long taskId, VwEtlTableWithSource table, String bizDateValue)
    {
        return targetAdapterRegistry.resolve(table).prepareBeforeRun(taskId, table, bizDateValue);
    }

    @Override
    public Connection getHiveConnect()
    {
        return hiveTargetAdapter.getHiveConnect();
    }

    @Override
    public DataSource getHiveDataSourceWithConfig(HiveConnectDto hiveConnectDto)
        throws MalformedURLException
    {
        return hiveTargetAdapter.getHiveDataSourceWithConfig(hiveConnectDto);
    }

    @Override
    public Long getMaxValue(VwEtlTableWithSource table, String columnName, String partValue)
    {
        return targetAdapterRegistry.resolve(table).getMaxValue(table, columnName, partValue);
    }

    @Override
    public String buildWriterJob(VwEtlTableWithSource table)
    {
        return targetAdapterRegistry.resolve(table).buildWriterJob(table);
    }
}
