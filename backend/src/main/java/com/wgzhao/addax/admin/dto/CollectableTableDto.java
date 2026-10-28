package com.wgzhao.addax.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * A source table that has no active collection definition for the selected route.
 *
 * @param state {@code AVAILABLE} when the route is free, {@code RETIRED} when a retired definition
 *              already owns it. A retired row is restored rather than created again.
 */
public record CollectableTableDto(
    String name,
    String comment,
    Long approxRowCount,
    String state,
    Long definitionId,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime retiredAt,
    String retiredBy)
{
}
