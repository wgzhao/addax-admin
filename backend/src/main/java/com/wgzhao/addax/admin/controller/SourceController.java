package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.DbConnectDto;
import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.service.SourceService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 数据源管理控制器，提供数据源及相关元数据的管理接口
 */
@RestController
@RequestMapping("/sources")
@AllArgsConstructor
public class SourceController
{
    private final SourceService sourceService;
    private final TableService tableService;

    /**
     * 查询所有数据源
     *
     * @return 数据源列表
     */
    @GetMapping("")
    public ResponseEntity<List<EtlSource>> list()
    {
        return ResponseEntity.ok(sourceService.findAll());
    }

    /**
     * 查询有效的数据源
     *
     * @return 有效的数据源列表
     */
    @GetMapping("/enabled")
    public ResponseEntity<List<EtlSource>> listEnabled()
    {
        return ResponseEntity.ok(sourceService.findEnabledSources());
    }

    /**
     * 新建数据源
     *
     * @param etlSource 数据源对象（请求体），字段详见 {@link EtlSource}
     * @return 新建的数据源对象（201 Created）
     */
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
     * @param id 数据源ID（路径参数）
     * @param etlSource 数据源对象（请求体），字段详见 {@link EtlSource}
     * @return 更新的记录数
     */
    @PutMapping("/{id}")
    public ResponseEntity<Integer> updateSource(@PathVariable int id, @RequestBody EtlSource etlSource)
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
     * @param id 数据源ID（路径参数），示例: 1
     * @return 数据源对象
     */
    @GetMapping("/{id}")
    public ResponseEntity<EtlSource> get(
        @PathVariable int id)
    {
        return sourceService.findById(id)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ApiException(404, "Source not found"));
    }

    /**
     * 删除数据源
     *
     * @param id 数据源ID（路径参数），示例: 1
     * @return 无内容响应（204）或错误（400/404）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable int id)
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
     * @param payload 连接参数（请求体），包含 url, username, password；示例: {"url":"jdbc:mysql://localhost:3306/test","username":"root","password":"123456"}
     * @return 是否连接成功
     */
    @PostMapping("/test-connect")
    public ResponseEntity<Boolean> testConnect(@RequestBody DbConnectDto payload)
    {
        boolean isConnected = DbUtil.testConnection(payload.url(), payload.username(), payload.password());
        return ResponseEntity.ok(isConnected);
    }

    /**
     * 检查编号是否存在
     *
     * @param code 数据源编号（查询参数），示例: SRC001
     * @return 编号是否存在
     */
    @GetMapping("/check-code")
    public ResponseEntity<Boolean> checkCode(
        @RequestParam(value = "code") String code)
    {
        if (code == null || code.isEmpty()) {
            return ResponseEntity.ok(false);
        }
        return ResponseEntity.ok(sourceService.checkCode(code));
    }

    /**
     * 查询采集源下所有数据库
     *
     * @param sourceId 数据源ID（路径参数），示例: 1
     * @return 数据库名称列表
     */
    @GetMapping("/{sourceId}/databases")
    public ResponseEntity<List<String>> listDatabases(
        @PathVariable int sourceId)
    {
        List<String> result = new ArrayList<>();
        EtlSource source = sourceService.getSource(sourceId);
        if (source == null) {
            throw new ApiException(400, "sourceId 对应的采集源不存在");
        }
        try (Connection connection = DbUtil.getConnection(source.getUrl(), source.getUsername(), source.getPass())) {
            assert connection != null;
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
     * @param sourceId 数据源ID（路径参数），示例: 1
     * @param dbName 数据库名（路径参数），示例: db
     * @return 表元数据列表
     */
    @GetMapping("/{sourceId}/databases/{dbName}/tables/uncollected")
    public ResponseEntity<List<TableMetaDto>> listUncollectedTables(
        @PathVariable int sourceId,
        @PathVariable String dbName)
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
