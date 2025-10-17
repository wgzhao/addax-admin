package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.DbConnectDto;
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
 * 数据源管理控制器，提供数据源及相关元数据的管理接口
 */
@Tag(name = "数据源管理", description = "数据源配置相关接口")
@RestController
@RequestMapping("/sources")
@AllArgsConstructor
public class SourceController
{
    /**
     * 数据源服务
     */
    private final SourceService sourceService;

    /**
     * 表服务
     */
    private final TableService tableService;


    /**
     * 查询所有数据源
     *
     * @return 数据源列表
     */
    @Operation(summary = "查询所有数据源", description = "返回所有数据源列表")
    @GetMapping("")
    public ResponseEntity<List<EtlSource>> list()
    {
        return ResponseEntity.ok(sourceService.findAll());
    }

    /**
     * 新建数据源
     *
     * @param etlSource 数据源对象
     * @return 新建的数据源对象
     */
    @Operation(summary = "新建数据源", description = "创建一个新的数据源")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "数据源对象",
            required = true,
            content = @Content(schema = @Schema(implementation = EtlSource.class))
    )

    @PostMapping("")
    public ResponseEntity<EtlSource> createSource(@RequestBody EtlSource etlSource)
    {
        if (etlSource.getId() > 0) {
            throw new ApiException(400, "Source ID must not be provided when creating a new source");
        }
        EtlSource saved = sourceService.create(etlSource);
        return ResponseEntity.status(201).body(saved);
    }

    /**
     * 保存数据源
     *
     * @param id 数据源ID
     * @param etlSource 数据源对象
     * @return 更新的记录数
     */
    @Operation(summary = "保存数据源", description = "保存数据源对象")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "数据源对象",
            required = true,
            content = @Content(schema = @Schema(implementation = EtlSource.class))
    )
    @PutMapping("/{id}")
    public ResponseEntity<Integer> updateSource(@PathVariable(value = "id") int id, @RequestBody EtlSource etlSource)
    {
        if (etlSource.getId() != id) {
            throw new ApiException(400, "ID in path and body do not match");
        }
        EtlSource existing = sourceService.findById(id).orElseThrow(() -> new ApiException(404, "Source not found"));
        // etlSource.code 不允许修改
        if (!Objects.equals(existing.getCode(), etlSource.getCode())) {
            throw new ApiException(400, "Source code cannot be modified");
        }
        sourceService.save(etlSource);

        return ResponseEntity.ok(1);
    }

    /**
     * 查询单个数据源
     *
     * @param id 数据源ID
     * @return 数据源对象
     */
    @Operation(summary = "查询单个数据源", description = "根据ID查询数据源")
    @GetMapping("/{id}")
    public ResponseEntity<EtlSource> get(
            @Parameter(description = "数据源ID", example = "1") @PathVariable(value = "id") int id)
    {
        return sourceService.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ApiException(404, "Source not found"));
    }

    /**
     * 删除数据源
     *
     * @param id 数据源ID
     * @return 响应实体
     */
    @Operation(summary = "删除数据源", description = "根据ID删除数据源")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "数据源ID", example = "1") @PathVariable("id") int id)
    {
        int tableCountBySourceId = tableService.getTableCountBySourceId(id);
        if (tableCountBySourceId > 0) {
            throw new ApiException(400, "Cannot delete source with associated tables");
        }
        if (sourceService.existsById(id)) {
            sourceService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        else {
            throw new ApiException(404, "Source not found");
        }
    }

    /**
     * 测试数据源连接
     *
     * @param payload 连接参数
     * @return 是否连接成功
     */
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
    @PostMapping("/test-connect")
    public ResponseEntity<Boolean> testConnect(@RequestBody DbConnectDto payload)
    {
        boolean isConnected = DbUtil.testConnection(payload.url(), payload.username(), payload.password());
        return ResponseEntity.ok(isConnected);
    }

    /**
     * 检查编号是否存在
     *
     * @param code 数据源编号
     * @return 编号是否存在
     */
    @Operation(summary = "检查编号是否存在", description = "检查数据源编号是否已存在")
    @GetMapping("/check-code")
    public ResponseEntity<Boolean> checkCode(
            @Parameter(description = "数据源编号", example = "SRC001", required = true) @RequestParam(value = "code") String code)
    {
        if (code == null || code.isEmpty()) {
            return ResponseEntity.ok(false);
        }
        return ResponseEntity.ok(sourceService.checkCode(code));
    }

    /**
     * 查询采集源下所有数据库
     *
     * @param sourceId 数据源ID
     * @return 数据库名称列表
     */
    @Operation(summary = "查询采集源下所有数据库", description = "根据 sourceId 查询该采集源下所有数据库名称")
    @GetMapping("/{sourceId}/databases")
    public ResponseEntity<List<String>> listDatabases(
            @Parameter(description = "数据源ID", example = "1") @PathVariable("sourceId") int sourceId)
    {
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
        }
        catch (SQLException e) {
            throw new ApiException(500, e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 查询未采集的表(含表注释)
     *
     * @param sourceId 数据源ID
     * @param dbName 数据库名
     * @return 表元数据列表
     */
    @Operation(summary = "查询未采集的表(含表注释)", description = "查询指定采集源和数据库下未采集的表名与表注释")
    @GetMapping("/{sourceId}/databases/{dbName}/tables/uncollected")
    public ResponseEntity<List<TableMetaDto>> listUncollectedTables(
            @Parameter(description = "数据源ID", example = "1") @PathVariable("sourceId") int sourceId,
            @Parameter(description = "数据库名", example = "db") @PathVariable("dbName") String dbName)
    {
        List<TableMetaDto> result;
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

        result = sourceService.getUncollectedTables(source, dbName, existsSet);
        if (result == null) {
            throw new ApiException(500, "获取未采集表失败");
        }
        return ResponseEntity.ok(result);
    }
}
