package com.wgzhao.addax.admin.controller

import com.wgzhao.addax.admin.dto.DbConnectDto
import com.wgzhao.addax.admin.dto.TableMetaDto
import com.wgzhao.addax.admin.exception.ApiException
import com.wgzhao.addax.admin.model.EtlSource
import com.wgzhao.addax.admin.service.SourceService
import com.wgzhao.addax.admin.service.TableService
import com.wgzhao.addax.admin.utils.DbUtil.testConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

/**
 * 数据源管理控制器，提供数据源及相关元数据的管理接口
 */
@Tag(name = "数据源管理", description = "数据源配置相关接口")
@RestController
@RequestMapping("/sources")
class SourceController(
    private val sourceService: SourceService,
    private val tableService: TableService
) {
    private val log = KotlinLogging.logger {}

    /**
     * 查询所有数据源
     *
     * @return 数据源列表
     */
    @Operation(summary = "查询所有数据源", description = "返回所有数据源列表")
    @GetMapping("")
    fun list(): ResponseEntity<List<EtlSource>> =
        ResponseEntity.ok(sourceService.findAll())

    /**
     * 新建数据源
     *
     * @param etlSource 数据源对象
     * @return 新建的数据源对象
     */
    @Operation(summary = "新建数据源", description = "创建一个新的数据源")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "数据源对象", required = true, content = [Content(schema = Schema(implementation = EtlSource::class))])
    @PostMapping("")
    fun createSource(@RequestBody etlSource: EtlSource): ResponseEntity<EtlSource> {
        if (etlSource.id > 0) throw ApiException(400, "Source ID must not be provided when creating a new source")
        val saved = sourceService.create(etlSource)
        return ResponseEntity.status(201).body(saved)
    }

    /**
     * 保存数据源
     *
     * @param id 数据源ID
     * @param etlSource 数据源对象
     * @return 更新的记录数
     */
    @Operation(summary = "保存数据源", description = "保存数据源对象")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "数据源对象", required = true, content = [Content(schema = Schema(implementation = EtlSource::class))])
    @PutMapping("/{id}")
    fun updateSource(@PathVariable id: Int, @RequestBody etlSource: EtlSource): ResponseEntity<Int> {
        if (etlSource.id != id) throw ApiException(400, "ID in path and body do not match")
        val existing = sourceService.findById(id).orElseThrow { ApiException(404, "Source not found") }
        if (existing.code != etlSource.code) throw ApiException(400, "Source code cannot be modified")
        sourceService.save(etlSource)
        return ResponseEntity.ok(1)
    }

    /**
     * 查询单个数据源
     *
     * @param id 数据源ID
     * @return 数据源对象
     */
    @Operation(summary = "查询单个数据源", description = "根据ID查询数据源")
    @GetMapping("/{id}")
    fun getSource(@PathVariable id: Int): ResponseEntity<EtlSource> =
        ResponseEntity.ok(sourceService.findById(id).orElseThrow { ApiException(404, "Source not found") })

    /**
     * 删除数据源
     *
     * @param id 数据源ID
     * @return 响应实体
     */
    @Operation(summary = "删除数据源", description = "根据ID删除数据源")
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(description = "数据源ID", example = "1") @PathVariable("id") id: Int
    ): ResponseEntity<Void?> {
        val tableCountBySourceId = tableService.getTableCountBySourceId(id)
        if (tableCountBySourceId > 0) {
            throw ApiException(400, "Cannot delete source with associated tables")
        }
        if (sourceService.existsById(id)) {
            sourceService.deleteById(id)
            return ResponseEntity.noContent().build<Void?>()
        } else {
            throw ApiException(404, "Source not found")
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
        content = [Content(schema = Schema(example = "{\"url\":\"jdbc:mysql://localhost:3306/test\",\"username\":\"root\",\"password\":\"123456\"}"))]
    )
    @PostMapping("/test-connect")
    fun testConnect(@RequestBody payload: DbConnectDto): ResponseEntity<String?> {
        val result = testConnection(payload.url, payload.username, payload.password)
        return if (result.first) ResponseEntity.ok(result.second) else ResponseEntity.badRequest().body(result.second)
    }

    /**
     * 检查编号是否存在
     *
     * @param code 数据源编号
     * @return 编号是否存在
     */
    @Operation(summary = "检查编号是否存在", description = "检查数据源编号是否已存在")
    @GetMapping("/check-code")
    fun checkCode(
        @Parameter(description = "数据源编号", example = "SRC001", required = true) @RequestParam(value = "code") code: String?
    ): ResponseEntity<Boolean?> {
        if (code == null || code.isEmpty()) {
            return ResponseEntity.ok<Boolean?>(false)
        }
        return ResponseEntity.ok<Boolean?>(sourceService.checkCode(code))
    }

    /**
     * 查询采集源下所有数据库
     *
     * @param sourceId 数据源ID
     * @return 数据库名称列表
     */
    @Operation(summary = "查询采集源下所有数据库", description = "根据 sourceId 查询该采集源下所有数据库名称")
    @GetMapping("/{sourceId}/databases")
    fun listDatabases(
        @Parameter(description = "数据源ID", example = "1") @PathVariable("sourceId") sourceId: Int
    ): ResponseEntity<MutableList<String?>?> {
        val result: MutableList<String?> = ArrayList<String?>()
        val source = sourceService.getSource(sourceId) ?: throw ApiException(400, "sourceId 对应的采集源不存在")
        try {
            DriverManager.getConnection(source.url, source.username, source.pass).use { connection ->
                val catalogs = connection.getMetaData().getCatalogs()
                while (catalogs.next()) {
                    result.add(catalogs.getString(1))
                }
            }
        } catch (e: SQLException) {
            throw ApiException(500, e.message)
        }
        return ResponseEntity.ok<MutableList<String?>?>(result)
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
    fun listUncollectedTables(
        @Parameter(description = "数据源ID", example = "1") @PathVariable("sourceId") sourceId: Int,
        @Parameter(description = "数据库名", example = "db") @PathVariable("dbName") dbName: String?
    ): ResponseEntity<MutableList<TableMetaDto?>?> {
        val result: MutableList<TableMetaDto?>?
        val existsTables: List<String> = tableService.getTablesBySidAndDb(sourceId, dbName) ?: ArrayList()
        // 为了尽量避免大小写带来的不一致，这里做一个双写的Set
        val existsSet: MutableSet<String?> = HashSet<String?>()
        for (t in existsTables) {
            existsSet.add(t)
            existsSet.add(t.lowercase(Locale.getDefault()))
        }
        val source = sourceService.getSource(sourceId) ?: throw ApiException(400, "sourceId 对应的采集源不存在")

        result = sourceService.getUncollectedTables(source, dbName, existsSet)
        if (result == null) {
            throw ApiException(500, "获取未采集表失败")
        }
        return ResponseEntity.ok<MutableList<TableMetaDto?>?>(result)
    }
}
