package com.wgzhao.addax.admin.dto;

public record HiveConnectDto(
        String url,
        String username,
        String password,
        String driverClassName,
        String driverPath
)
{}