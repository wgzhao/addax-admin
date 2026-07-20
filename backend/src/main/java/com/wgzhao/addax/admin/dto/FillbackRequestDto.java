package com.wgzhao.addax.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Fillback (补数) request payload.
 *
 * @param tids      table IDs to fillback
 * @param startDate fillback start date (inclusive)
 * @param endDate   fillback end date (inclusive)
 */
public record FillbackRequestDto(
    List<Long> tids,
    LocalDate startDate,
    LocalDate endDate
)
{
}
