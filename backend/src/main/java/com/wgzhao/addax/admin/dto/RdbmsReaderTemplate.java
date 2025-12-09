package com.wgzhao.addax.admin.dto;

import lombok.*;

import java.util.List;

/**
 * 通用关系型数据库读取模板，适配 Addax
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class RdbmsReaderTemplate {
    private String name;
    private String username;
    private String password;
    private String jdbcUrl;
    private String table;
    private List<String> column;
    private boolean autoPk = true;
    private int fetchSize = 20480;
    private String where = "1=1";
    private String splitPk = "";

    public String toJson() {
        return """
                {
                    "name": "%s",
                    "parameter": {
                        "username": "%s",
                        "password": "%s",
                        "column": [%s],
                        "where": "%s",
                        "autoPk": "%s",
                        "splitPk": "%s",
                        "connection": {
                            "jdbcUrl": [ "%s" ],
                            "table": [ "%s" ]
                        },
                        "fetchSize": %d
                    }
                }
                """.formatted(name, username, password,
                String.join(", ", column),
                where, autoPk, splitPk, jdbcUrl, table, fetchSize
        );
    }
}

