package com.wgzhao.addax.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequestDTO(
    @NotBlank(message = "用户名不能为空") String username,
    @NotBlank(message = "密码不能为空") String password
) {}
