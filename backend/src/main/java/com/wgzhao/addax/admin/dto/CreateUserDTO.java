package com.wgzhao.addax.admin.dto;

public record CreateUserDTO(String username, String password, Boolean enabled, String authority)
{
}
