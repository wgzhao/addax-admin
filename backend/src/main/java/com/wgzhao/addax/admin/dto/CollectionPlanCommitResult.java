package com.wgzhao.addax.admin.dto;

public record CollectionPlanCommitResult(
    Long tableId,
    String sourceTable,
    String targetTable,
    String status,
    String message
)
{
    public static CollectionPlanCommitResult created(Long id, String source, String target, String message)
    {
        return new CollectionPlanCommitResult(id, source, target, "CREATED", message);
    }

    public static CollectionPlanCommitResult failed(Long id, String source, String target, String message)
    {
        return new CollectionPlanCommitResult(id, source, target, "FAILED", message);
    }
}
