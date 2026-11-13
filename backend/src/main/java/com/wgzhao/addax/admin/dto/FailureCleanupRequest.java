package com.wgzhao.addax.admin.dto;

import java.time.LocalDate;

/**
 * Cleanup is always scoped by a cutoff date. There is deliberately no "delete everything" form.
 */
public record FailureCleanupRequest(
    LocalDate before,
    Long tid,
    Integer sourceId,
    String stage,
    boolean dryRun
)
{
}
