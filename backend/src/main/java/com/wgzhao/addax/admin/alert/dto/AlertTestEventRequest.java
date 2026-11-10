package com.wgzhao.addax.admin.alert.dto;

/**
 * Optional source context lets an operator exercise SOURCE/TABLE rule scopes
 * from the test-event endpoint without waiting for a real collection failure.
 */
public record AlertTestEventRequest(
    String eventType,
    String message,
    String sourceDb,
    String sourceTable,
    Long tid
)
{
}
