package com.wgzhao.addax.admin.dto;

/**
 * A typed answer to "why did this table fail".
 *
 * <p>The old endpoint returned a bare string and used the same English fallback for every empty
 * case, so the UI could not distinguish "never failed" from "evidence expired" or "not allowed".
 * {@code reason} carries that distinction.</p>
 */
public record LatestFailureDto(
    boolean available,
    String reason,
    FailureDto failure,
    Occurrences occurrences,
    Evidence evidence,
    String hint
)
{
    public static final String NO_FAILURE = "NO_FAILURE";
    public static final String NO_EVIDENCE_FOR_STATE = "NO_EVIDENCE_FOR_STATE";
    public static final String PURGED = "PURGED";

    public record Occurrences(long count, String firstSeenAt, String lastSeenAt)
    {
        public static Occurrences empty()
        {
            return new Occurrences(0, null, null);
        }
    }

    /**
     * Availability of each evidence layer: {@code available}, {@code purged}, {@code missing} or
     * {@code restricted}.
     */
    public record Evidence(String detail, String log, String execution)
    {
    }
}
