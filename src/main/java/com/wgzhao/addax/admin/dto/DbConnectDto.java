package com.wgzhao.addax.admin.dto;

/**
 * 数据库连接信息传输对象
 *
 */
public record DbConnectDto(String url, String username, String password)
{
}
