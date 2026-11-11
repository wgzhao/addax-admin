package com.wgzhao.addax.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Fillback (补数) request payload.
 *
 * <p>{@code dates} takes precedence when present: the detail page lets the user pick several
 * non-contiguous days from a calendar, so a start/end pair alone cannot express the request.
 *
 * @param tids      table IDs to fillback
 * @param startDate fillback start date (inclusive), used when {@code dates} is absent
 * @param endDate   fillback end date (inclusive), used when {@code dates} is absent
 * @param dates     explicit target dates, duplicate and null values are ignored
 */
public record FillbackRequestDto(
    List<Long> tids,
    LocalDate startDate,
    LocalDate endDate,
    List<LocalDate> dates
)
{
}
