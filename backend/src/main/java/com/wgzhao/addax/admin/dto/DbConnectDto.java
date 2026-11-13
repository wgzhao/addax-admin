package com.wgzhao.addax.admin.dto;

import tools.jackson.databind.JsonNode;

/**
 * 数据库连接信息传输对象。
 * sourceId 可选，当 password 为哨兵值时用于从 DB 取回真实密码。
 * dbType/connectConfig 用于非 JDBC 采集源（MongoDB、Elasticsearch）；JDBC 源两者都可以为空。
 */
public record DbConnectDto(
    String url,
    String username,
    String password,
    Integer sourceId,
    String dbType,
    JsonNode connectConfig
)
{
    /** Backward-compatible constructor for JDBC-only callers. */
    public DbConnectDto(String url, String username, String password, Integer sourceId)
    {
        this(url, username, password, sourceId, null, null);
    }
}
