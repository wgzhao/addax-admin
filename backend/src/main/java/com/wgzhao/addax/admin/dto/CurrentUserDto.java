package com.wgzhao.addax.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The current account as the frontend needs it: profile, role and the source grants that drive UI
 * gating.
 */
public record CurrentUserDto(
    String username,
    String displayName,
    String email,
    String remark,
    boolean enabled,
    List<String> authorities,
    List<SourceGrantDto> grants,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastLoginAt,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime passwordChangedAt)
{
}
