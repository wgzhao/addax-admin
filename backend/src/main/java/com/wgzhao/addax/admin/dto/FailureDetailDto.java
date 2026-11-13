package com.wgzhao.addax.admin.dto;

/**
 * One failure fact plus the evidence availability and repeat count the drawer needs.
 */
public record FailureDetailDto(
    FailureDto failure,
    LatestFailureDto.Occurrences occurrences,
    LatestFailureDto.Evidence evidence,
    String hint
)
{
}
