package com.wgzhao.addax.admin.dto;

/**
 * Read projection of a source grant. The source name is included so the account page can render the
 * grant without a second lookup.
 */
public record SourceGrantDto(int sourceId, String sourceName, String accessLevel)
{
}
