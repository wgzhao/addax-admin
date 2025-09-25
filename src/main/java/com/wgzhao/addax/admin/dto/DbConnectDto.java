package com.wgzhao.addax.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据库连接信息传输对象
 *
 */
@Getter
@Setter
@AllArgsConstructor
public class DbConnectDto
{
    private final String url;
    private final  String username;
    private final String password;
}
