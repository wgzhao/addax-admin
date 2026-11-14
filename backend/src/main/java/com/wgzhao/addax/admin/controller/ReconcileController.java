package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.ReconcileRunRequest;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlReconcile;
import com.wgzhao.addax.admin.repository.EtlReconcileRepo;
import com.wgzhao.addax.admin.service.AuthzService;
import com.wgzhao.addax.admin.service.ReconcileService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集对账：单表对账历史查询与手动触发。
 */
@RestController
@RequestMapping("/reconciles")
@AllArgsConstructor
public class ReconcileController
{
    private final ReconcileService reconcileService;
    private final EtlReconcileRepo reconcileRepo;
    private final AuthzService authzService;
    private final AuditRecorder auditRecorder;

    @GetMapping
    public List<EtlReconcile> list(@RequestParam("tid") long tid)
    {
        authzService.requireTableRead(tid);
        return reconcileRepo.findTop15ByTidOrderByIdDesc(tid);
    }

    @PostMapping("/run")
    public EtlReconcile run(@RequestBody ReconcileRunRequest request)
    {
        if (request == null || request.tid() == null || request.bizDate() == null)
        {
            throw new ApiException(400, "必须指定 tid 和 bizDate");
        }
        authzService.requireTableOperate(request.tid());
        EtlReconcile record = reconcileService.reconcileFromStatistic(request.tid(), request.bizDate());
        if (record == null)
        {
            throw new ApiException(404, "该业务日期没有采集统计，无法对账");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("bizDate", request.bizDate().toString());
        metadata.put("status", record.getStatus());
        metadata.put("sourceRows", record.getSourceRows());
        metadata.put("targetRows", record.getTargetRows());
        auditRecorder.success("RECONCILE_RUN", "DATA_ACCESS", "INFO",
            "COLLECTION_TABLE", request.tid(), String.valueOf(request.tid()),
            null, record, metadata);
        return record;
    }
}
