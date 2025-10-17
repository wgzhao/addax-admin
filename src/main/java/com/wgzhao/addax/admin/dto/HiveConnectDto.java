package com.wgzhao.addax.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class HiveConnectDto {
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    private String driverPath;
}
