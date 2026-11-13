package com.wgzhao.addax.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public record UserAdminDto(
    String username,
    String displayName,
    String email,
    String remark,
    boolean enabled,
    List<String> authorities,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastLoginAt,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime passwordChangedAt)
{
}
