package com.wgzhao.addax.admin.exception;

import com.wgzhao.addax.admin.dto.TargetContractReport;

public class TargetContractViolationException extends RuntimeException
{
    private final TargetContractReport report;
    private final Long executionId;

    public TargetContractViolationException(TargetContractReport report)
    {
        this(report, null);
    }

    public TargetContractViolationException(TargetContractReport report, Long executionId)
    {
        super(report.summary());
        this.report = report;
        this.executionId = executionId;
    }

    public TargetContractReport getReport()
    {
        return report;
    }

    /**
     * Execution that was already persisted when the contract blocked the run, so the failure fact
     * can link to the snapshot.
     */
    public Long getExecutionId()
    {
        return executionId;
    }
}
