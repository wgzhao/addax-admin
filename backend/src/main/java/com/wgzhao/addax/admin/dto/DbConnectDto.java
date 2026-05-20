package com.wgzhao.addax.admin.dto;

/**
 * 数据库连接信息传输对象
 * sourceId 可选，当 password 为哨兵值时用于从 DB 取回真实密码
 */
public record DbConnectDto(String url, String username, String password, Integer sourceId)
{
}
