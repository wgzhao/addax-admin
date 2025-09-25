package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@AllArgsConstructor
public class SourceService
{
    private final EtlSourceRepo etlSourceRepo;
    private final CollectionSchedulingService collectionSchedulingService;

    public Integer getValidSources()
    {
        return etlSourceRepo.countByEnabled(true);
    }

    public EtlSource getSource(Integer sid)
    {
        return etlSourceRepo.findById(sid).orElse(null);
    }

    public boolean checkCode(String code)
    {
        return etlSourceRepo.existsByCode(code);
    }

    public List<EtlSource> findAll()
    {
        return etlSourceRepo.findAll();
    }

    public Optional<EtlSource> findById(int id)
    {
        return etlSourceRepo.findById(id);
    }

    public EtlSource save(EtlSource etlSource, boolean updateSchedule)
    {
        if (updateSchedule) {
            collectionSchedulingService.cancelTask(etlSource.getCode());
            collectionSchedulingService.scheduleOrUpdateTask(etlSource);
        }
        return etlSourceRepo.save(etlSource);
    }

    public void deleteById(int id)
    {
        etlSourceRepo.deleteById(id);
    }

    public boolean existsById(int id)
    {
        return etlSourceRepo.existsById(id);
    }

    public void saveAll(List<EtlSource> sources)
    {
        etlSourceRepo.saveAll(sources);
    }

    public EtlSource create(EtlSource etlSource)
    {
        EtlSource save = etlSourceRepo.save(etlSource);
        // 新采集源创建时，默认创建一个同步任务
        collectionSchedulingService.scheduleOrUpdateTask(etlSource);
        return save;
    }

    public List<TableMetaDto> getUncollectedTables(EtlSource source, String dbName, Set<String> existsSet)
            throws SQLException
    {
        List<TableMetaDto> result = new java.util.ArrayList<>();
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
                }
                catch (SQLException ignore) { /* fallback 不可用时忽略 */ }
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
                        if (!schema.isEmpty()) {
                            commentFallback.put(schema + "." + tbl, cmt);
                        }
                        commentFallback.put(tbl, cmt);
                    }
                }
                catch (SQLException ignore) {
                }
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
                        if (!schema.isEmpty()) {
                            commentFallback.put(schema + "." + tbl, cmt);
                        }
                        commentFallback.put(tbl, cmt);
                    }
                }
                catch (SQLException ignore) {
                }
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
                        if (!schema.isEmpty()) {
                            commentFallback.put(schema + "." + tbl, cmt);
                        }
                        commentFallback.put(tbl, cmt);
                    }
                }
                catch (SQLException ignore) {
                }
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
        }
        return result;
    }
}
