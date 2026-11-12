package com.wgzhao.addax.admin.dto;

import java.util.List;

/**
 * The current account as the frontend needs it: role plus the source grants that drive UI gating.
 */
public record CurrentUserDto(
    String username,
    boolean enabled,
    List<String> authorities,
    List<SourceGrantDto> grants)
{
}
