package com.wgzhao.addax.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(name = "TableMetaDto", description = "数据库表元信息（仅名称与注释）")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableMetaDto {
    @Schema(description = "表名")
    private String name;

    @Schema(description = "表注释")
    private String comment;
}

