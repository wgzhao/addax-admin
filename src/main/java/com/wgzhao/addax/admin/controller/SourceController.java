package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.service.SourceService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.utils.DbUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 数据源配置接口
 */
@Tag(name = "数据源管理", description = "数据源配置相关接口")
@RestController
@RequestMapping("/sources")
@AllArgsConstructor
public class SourceController {
    private final SourceService sourceService;
    private final TableService tableService;

    @Operation(summary = "查询所有数据源", description = "返回所有数据源列表")
    @GetMapping("")
    public ResponseEntity<List<EtlSource>> list() {
        return ResponseEntity.ok(sourceService.findAll());
    }

    @Operation(summary = "新建数据源", description = "创建一个新的数据源")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "数据源对象",
        required = true,
        content = @Content(schema = @Schema(implementation = EtlSource.class))
    )

    @PostMapping("")
    public ResponseEntity<EtlSource> createSource(@RequestBody EtlSource etlSource) {
        if (etlSource.getId()  > 0) {
            throw new ApiException(400, "Source ID must not be provided when creating a new source");
        }
        EtlSource saved = sourceService.create(etlSource);
        return ResponseEntity.status(201).body(saved);
    }

    @Operation(summary = "保存数据源", description = "保存数据源对象")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "数据源对象",
            required = true,
            content = @Content(schema = @Schema(implementation = EtlSource.class))
    )
    @PutMapping("/{id}")
    public ResponseEntity<Integer> updateSource(@PathVariable(value = "id") int id, @RequestBody EtlSource etlSource) {
        if (etlSource.getId() != id) {
            throw new ApiException(400, "ID in path and body do not match");
        }
        EtlSource existing = sourceService.findById(id).orElseThrow(() -> new ApiException(404, "Source not found"));
        // etlSource.code 不允许修改
        if (!Objects.equals(existing.getCode(), etlSource.getCode())) {
            throw new ApiException(400, "Source code cannot be modified");
        }
        boolean needUpdateSchedule = existing.getStartAt() == etlSource.getStartAt();
        sourceService.save(etlSource, needUpdateSchedule);
        return ResponseEntity.ok(1);
    }

    @Operation(summary = "查询单个数据源", description = "根据ID查询数据源")
    @GetMapping("/{id}")
    public ResponseEntity<EtlSource> get(
            @Parameter(description = "数据源ID", example = "1") @PathVariable(value = "id") int id) {
        return sourceService.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ApiException(404, "Source not found"));
    }

    @Operation(summary = "删除数据源", description = "根据ID删除数据源")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "数据源ID", example = "1") @PathVariable("id") int id) {
        if (sourceService.existsById(id)) {
            sourceService.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            throw new ApiException(404, "Source not found");
        }
    }


    @Operation(summary = "测试数据源连接", description = "测试指定参数的数据源连接是否可用")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "连接参数",
        required = true,
        content = @Content(
            schema = @Schema(
                example = "{\"url\":\"jdbc:mysql://localhost:3306/test\",\"username\":\"root\",\"password\":\"123456\"}"
            )
        )
    )
    @PostMapping("/testConnect")
    public ApiResponse<Boolean> testConnect(@RequestBody Map<String, String> payload) {
        boolean isconn = DbUtil.testConnection(payload.get("url"), payload.get("username"), payload.get("password"));
        return ApiResponse.success(isconn);
    }

    @Operation(summary = "检查编号是否存在", description = "检查数据源编号是否已存在")
    @GetMapping("")
    public ApiResponse<Boolean> checkCode(
            @Parameter(description = "数据源编号", example = "SRC001", required = true) @RequestParam(value = "code") String code) {
        if (code == null || code.isEmpty()) {
            return ApiResponse.success(false);
        }
        return ApiResponse.success(sourceService.checkCode(code));
    }

    @Operation(summary = "查询采集源下所有数据库", description = "根据 sourceId 查询该采集源下所有数据库名称")
    @GetMapping("/{sourceId}/databases")
    public ResponseEntity<List<String>> listDatabases(
            @Parameter(description = "数据源ID", example = "1") @PathVariable("sourceId") int sourceId) {
        List<String> result = new ArrayList<>();
        EtlSource source = sourceService.getSource(sourceId);
        if (source == null) {
            throw new ApiException(400, "sourceId 对应的采集源不存在");
        }
        try (Connection connection = DriverManager.getConnection(source.getUrl(), source.getUsername(), source.getPass())) {
            ResultSet catalogs = connection.getMetaData().getCatalogs();
            while (catalogs.next()) {
                result.add(catalogs.getString(1));
            }
        } catch (SQLException e) {
            throw new ApiException(500, e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "查询未采集的表(含表注释)", description = "查询指定采集源和数据库下未采集的表名与表注释")
    @GetMapping("/{sourceId}/databases/{dbName}/tables/uncollected")
    public ResponseEntity<List<TableMetaDto>> listUncollectedTables(
            @Parameter(description = "数据源ID", example = "1") @PathVariable("sourceId") int sourceId,
            @Parameter(description = "数据库名", example = "testdb") @PathVariable("dbName") String dbName) {
        List<TableMetaDto> result = new ArrayList<>();
        List<String> existsTables = tableService.getTablesBySidAndDb(sourceId, dbName);
        // 为了尽量避免大小写带来的不一致，这里做一个双写的Set
        Set<String> existsSet = new HashSet<>();
        for (String t : existsTables) {
            existsSet.add(t);
            existsSet.add(t.toLowerCase());
        }
        EtlSource source = sourceService.getSource(sourceId);
        if (source == null) {
            throw new ApiException(400, "sourceId 对应的采集源不存在");
        }
        try (Connection connection = DriverManager.getConnection(source.getUrl(), source.getUsername(), source.getPass())) {
            // 各数据库注释回退：构建 commentFallback，key 优先为 schema.table，其次为 table
            Map<String, String> commentFallback = new HashMap<>();
            String url = Optional.ofNullable(source.getUrl()).orElse("").toLowerCase();
            // MySQL: information_schema.tables
            if (url.startsWith("jdbc:mysql")) {
                try (var ps = connection.prepareStatement(
                        "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.tables WHERE TABLE_SCHEMA = ?")) {
                    ps.setString(1, dbName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String tbl = rs.getString("TABLE_NAME");
                            String cmt = Optional.ofNullable(rs.getString("TABLE_COMMENT")).orElse("");
                            commentFallback.put(tbl, cmt);
                        }
                    }
                } catch (SQLException ignore) { /* fallback 不可用时忽略 */ }
            }
            // PostgreSQL: pg_class + pg_namespace + obj_description
            else if (url.startsWith("jdbc:postgresql")) {
                String sql = "SELECT n.nspname AS schema, c.relname AS table, obj_description(c.oid) AS comment " +
                             "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE c.relkind='r'";
                try (var ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String schema = Optional.ofNullable(rs.getString("schema")).orElse("");
                        String tbl = rs.getString("table");
                        String cmt = Optional.ofNullable(rs.getString("comment")).orElse("");
                        if (!schema.isEmpty()) commentFallback.put(schema + "." + tbl, cmt);
                        commentFallback.put(tbl, cmt);
                    }
                } catch (SQLException ignore) { }
            }
            // Oracle: ALL_TAB_COMMENTS（含 owner）
            else if (url.startsWith("jdbc:oracle")) {
                String sql = "SELECT OWNER AS schema, TABLE_NAME, COMMENTS FROM ALL_TAB_COMMENTS WHERE TABLE_TYPE='TABLE'";
                try (var ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String schema = Optional.ofNullable(rs.getString("schema")).orElse("");
                        String tbl = rs.getString("TABLE_NAME");
                        String cmt = Optional.ofNullable(rs.getString("COMMENTS")).orElse("");
                        // Oracle 标识符默认大写，元数据中 TABLE_SCHEM/TABLE_NAME 多为大写
                        if (!schema.isEmpty()) commentFallback.put(schema + "." + tbl, cmt);
                        commentFallback.put(tbl, cmt);
                    }
                } catch (SQLException ignore) { }
            }
            // SQL Server: sys.tables + sys.schemas + sys.extended_properties('MS_Description')
            else if (url.startsWith("jdbc:sqlserver")) {
                String sql = "SELECT s.name AS schema, t.name AS table, CAST(ep.value AS NVARCHAR(4000)) AS comment " +
                             "FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id " +
                             "LEFT JOIN sys.extended_properties ep ON ep.major_id = t.object_id AND ep.minor_id = 0 AND ep.class=1 AND ep.name='MS_Description'";
                try (var ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String schema = Optional.ofNullable(rs.getString("schema")).orElse("");
                        String tbl = rs.getString("table");
                        String cmt = Optional.ofNullable(rs.getString("comment")).orElse("");
                        if (!schema.isEmpty()) commentFallback.put(schema + "." + tbl, cmt);
                        commentFallback.put(tbl, cmt);
                    }
                } catch (SQLException ignore) { }
            }

            // 按元数据读取所有表
            ResultSet tables = connection.getMetaData().getTables(dbName, null, "%", new String[] {"TABLE"});
            while (tables.next()) {
                String tblName = tables.getString("TABLE_NAME");
                String schema = Optional.ofNullable(tables.getString("TABLE_SCHEM")).orElse("");
                if (existsSet.contains(tblName) || existsSet.contains(tblName.toLowerCase())) {
                    continue;
                }
                String remarks = Optional.ofNullable(tables.getString("REMARKS")).orElse("");
                if (remarks.isEmpty()) {
                    String key1 = !schema.isEmpty() ? (schema + "." + tblName) : tblName;
                    remarks = commentFallback.getOrDefault(key1, commentFallback.getOrDefault(tblName, ""));
                }
                result.add(new TableMetaDto(tblName, remarks));
            }
        } catch (SQLException e) {
            throw new ApiException(500, e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
