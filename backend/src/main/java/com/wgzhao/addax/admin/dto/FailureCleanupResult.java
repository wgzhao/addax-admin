package com.wgzhao.addax.admin.dto;

public record FailureCleanupResult(long matched, long deleted, boolean dryRun)
{
}
