package com.wgzhao.addax.admin.dto;

public record VwImpEtlListDto(
        String tid,
        String destOwner,
        String sysName,
        String souOwner,
        String destTablename,
        String flag,
        int retryCnt,
        int runtime,
        String filterColumn
) {
}
