package com.wgzhao.addax.admin.dto;

public record UpdateUserDTO(
    String password,
    String authority,
    String displayName,
    String email,
    String remark)
{
}
