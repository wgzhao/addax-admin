package com.wgzhao.addax.admin.dto;

/**
 * One source grant in an admin grant-replacement request. {@code accessLevel} is VIEW or OPERATE.
 */
public record SourceGrantInput(int sourceId, String accessLevel)
{
}
