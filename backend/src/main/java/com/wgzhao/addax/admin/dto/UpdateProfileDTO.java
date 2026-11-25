package com.wgzhao.addax.admin.dto;

/**
 * Self-service profile edit. Only descriptive fields are exposed: role, enabled state and password
 * stay admin-controlled (password has its own endpoint that re-checks the old one).
 */
public record UpdateProfileDTO(
    String displayName,
    String email,
    String remark)
{
}
