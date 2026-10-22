package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.dto.TargetTableMetadata;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import com.wgzhao.addax.admin.service.target.TargetAdapterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TargetContractServiceTest
{
    @Test
    void partitionColumnLeakedIntoColumnsIsNotReportedAsUnmapped() throws Exception
    {
        TargetAdapterRegistry registry = mock(TargetAdapterRegistry.class);
        TargetAdapter adapter = mock(TargetAdapter.class);
        ColumnMappingService mappings = mock(ColumnMappingService.class);
        RiskLogService risks = mock(RiskLogService.class);
        TargetContractService service = new TargetContractService(registry, mappings, risks);

        VwEtlTableWithSource table = new VwEtlTableWithSource();
        table.setId(54L);
        table.setTargetDb("odsmd");
        table.setTargetTable("order_statistic");
        table.setTargetOptions(new ObjectMapper().readTree("""
            {"hdfs":{"partitionBindings":[
              {"name":"logdate","valueType":"BIZ_DATE","format":"yyyyMMdd"}
            ]}}
            """));

        TargetTableMetadata target = new TargetTableMetadata(
            true,
            List.of(
                new TargetTableMetadata.TargetColumn("id", "decimal(19,0)", 0, 0, true, null, false, 1),
                new TargetTableMetadata.TargetColumn("logdate", "string", 0, 0, true, null, false, 2)
            ),
            List.of(new TargetTableMetadata.TargetPartition("logdate", "string")),
            Set.of(),
            Set.of()
        );
        EtlColumn id = new EtlColumn();
        id.setColumnName("id");
        id.setTargetType("decimal");
        id.setTargetTypeFull("decimal(19,0)");

        when(registry.resolve(table)).thenReturn(adapter);
        when(adapter.getType()).thenReturn("HDFS");
        when(adapter.inspectTable(table)).thenReturn(target);
        when(mappings.resolve(54L)).thenReturn(List.of(
            new ColumnMappingService.ResolvedColumnMapping(
                1L, "id", "id", 1, true, id, null)
        ));

        var report = service.check(table);

        assertTrue(report.findings().stream().noneMatch(finding ->
            "TARGET_EXTRA_COLUMN".equals(finding.code()) && "logdate".equals(finding.column())));
    }
}
