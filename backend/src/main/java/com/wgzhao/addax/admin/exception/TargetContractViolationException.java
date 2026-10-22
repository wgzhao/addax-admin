package com.wgzhao.addax.admin.exception;

import com.wgzhao.addax.admin.dto.TargetContractReport;

public class TargetContractViolationException extends RuntimeException
{
    private final TargetContractReport report;

    public TargetContractViolationException(TargetContractReport report)
    {
        super(report.summary());
        this.report = report;
    }

    public TargetContractReport getReport()
    {
        return report;
    }
}
