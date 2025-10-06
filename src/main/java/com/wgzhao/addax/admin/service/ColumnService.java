package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;

/**
 * 采集表字段信息服务类，负责采集表字段的增删改查及同步等业务操作
 */
@Service
@Slf4j
@AllArgsConstructor
public class ColumnService
{
    private final EtlColumnRepo etlColumnRepo;
    private final DictService dictService;
    private final EtlJourService jourService;

    /**
     * 获取指定采集表的所有字段信息
     * @param tid 采集表ID
     * @return 字段列表
     */
    public List<EtlColumn> getColumns(long tid) {
        return etlColumnRepo.findAllByTidOrderByColumnId(tid);
    }

    /**
     * 更新当前表的字段信息，主要涉及到源表字段的变更
     * 逻辑：
     * 1. 新增字段一定是追加在最后
     * 2. 删除字段则将源字段名设置为 __deleted__ 前缀
     * 3. 字段类型变更则同步更新并记录到风险表
     * 4. 新增字段直接插入
     * @param etlTable 采集表视图对象
     * @return 0 表示无需更新, 1 表示字段有更新, -1 表示更新失败
     */
    @Transactional
    public int updateTableColumns(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null) {
            return 0;
        }
        List<EtlColumn> existingColumns = etlColumnRepo.findAllByTidOrderByColumnId(etlTable.getId());
        if (existingColumns == null || existingColumns.isEmpty()) {
            // 第一次创建，直接全量写入
            return createTableColumns(etlTable) ? 1 : -1;
        }
        log.info("updating table columns for tid {}.{} ({})", etlTable.getSourceDb(), etlTable.getSourceTable(), etlTable.getId());
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_COLUMN, null);
        // 获取源表的字段信息

        Map<String, String> hiveTypeMapping = dictService.getHiveTypeMapping();
        String sql = "select * from `" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "` where 1=0";

        boolean changed = false;

        try (Connection connection = DriverManager.getConnection(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass());
                ResultSet rs = connection.createStatement().executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();

            // 构造源端列信息列表（保持顺序）
            List<EtlColumn> sourceCols = new ArrayList<>(n);
            EtlColumn sc;
            for (int i = 1; i <= n; i++) {
                sc = getEtlColumn(etlTable, i, md, connection, hiveTypeMapping);
                sourceCols.add(sc);
            }

            // 双指针对齐比较
            int o = 0; // 当前已经存在的字段索引 index
            int s = 0; // 当前采集表源字段索引 index
            int m = existingColumns.size();

            while (o < m && s < n) {
                EtlColumn oc = existingColumns.get(o);
                // 跳过已删除占位
                if (isDeletedPlaceholder(oc.getColumnName())) {
                    o++;
                    continue;
                }
                sc = sourceCols.get(s);
                if (Objects.equals(oc.getColumnName(), sc.getColumnName())) {
                    // 名称一致 -> 检查类型变化
                    boolean typeChanged = !Objects.equals(oc.getSourceType(), sc.getSourceType())
                            || notEq(oc.getDataPrecision(), sc.getDataPrecision())
                            || notEq(oc.getDataScale(), sc.getDataScale())
                            || oc.getDataLength() != sc.getDataLength();
                    if (typeChanged) {
                        // 记录风险日志
                        log.warn("RISK[COLUMN_TYPE_CHANGE] tid={}, table={}.{}, column={}, srcType: {}(len={},p={},s={}) -> {}(len={},p={},s={})",
                                etlTable.getId(), etlTable.getSourceDb(), etlTable.getSourceTable(), sc.getColumnName(),
                                oc.getSourceType(), oc.getDataLength(), nvl(oc.getDataPrecision()), nvl(oc.getDataScale()),
                                sc.getSourceType(), sc.getDataLength(), nvl(sc.getDataPrecision()), nvl(sc.getDataScale()));
                        // 同步更新字段类型映射
                        oc.setSourceType(sc.getSourceType());
                        oc.setDataLength(sc.getDataLength());
                        oc.setDataPrecision(sc.getDataPrecision());
                        oc.setDataScale(sc.getDataScale());
                        oc.setColComment(sc.getColComment());
                        oc.setTargetType(sc.getTargetType());
                        oc.setTargetTypeFull(sc.getTargetTypeFull());
                        etlColumnRepo.save(oc);
                        changed = true;
                    } // else 类型未变化，不做处理
                    o++; s++;
                } else {
                    // 名称不一致 -> 视为源删除了 origin 当前位置的列
                    String placeholder = DELETED_PLACEHOLDER_PREFIX  + oc.getColumnName();
                    oc.setColumnName(placeholder);
                    // 这里要注意该表必须有主键，否则会变成新增记录
                    etlColumnRepo.save(oc);
                    changed = true;
                    o++;
                    // 注意：不前进 s（新增只允许在末尾追加）
                }
            }

            // 剩余历史列 -> 全部标记为删除占位
            while (o < m) {
                EtlColumn oc = existingColumns.get(o++);
                if (!isDeletedPlaceholder(oc.getColumnName())) {
                    String placeholder = DELETED_PLACEHOLDER_PREFIX + oc.getColumnName();
                    oc.setColumnName(placeholder);
                    etlColumnRepo.save(oc);
                    changed = true;
                }
            }

            // 剩余源列 -> 末尾追加为新增列
            int nextId = m; // 现有最大 columnId 基本等于 m（顺序创建）
            while (s < n) {
                sc = sourceCols.get(s++);
                // 复制 sc 的所有属性到 nc，然后只设置 columnId
                EtlColumn nc = new EtlColumn();
                BeanUtils.copyProperties(sc, nc);
                // 只设置 columnId
                nc.setColumnId(++nextId);
                etlColumnRepo.save(nc);
                changed = true;
            }
        }
        catch (SQLException e) {
            jourService.failJour(etlJour, e.getMessage());
            log.error("failed to update table columns for tid {}", etlTable.getId(), e);
            return -1;
        }
        log.info("table columns updated for tid {}, changed={}", etlTable.getId(), changed);
        jourService.successJour(etlJour);
        return changed ? 1 : 0;
    }

    private static boolean isDeletedPlaceholder(String name) {
        return name != null && name.startsWith(DELETED_PLACEHOLDER_PREFIX);
    }

    private static boolean notEq(Integer a, Integer b) {
        return !Objects.equals(a, b);
    }

    private static int nvl(Integer v) { return v == null ? 0 : v; }
    private static String nvlStr(String v) { return v == null ? "" : v; }

    /**
     * 当新增采集表时，添加表的字段信息到 etl_column 表，他包含了源表的字段信息和目标表的字段信息
     * @param etlTable etl_table 表记录
     * @return true 成功，false 失败
     */
    @Transactional
    public boolean createTableColumns(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null) {
            return false;
        }
        log.info("first add table columns for tid {}.{} ({})", etlTable.getSourceDb(), etlTable.getSourceTable(), etlTable.getId());

        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.CREATE_COLUMN, null);

        Map<String, String> hiveTypeMapping = dictService.getHiveTypeMapping();
        String sql = "select * from `" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "` where 1=0";
        try (Connection connection = DriverManager.getConnection(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass());
                ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                EtlColumn etlColumn = getEtlColumn(etlTable, i, metaData, connection, hiveTypeMapping);
                etlColumnRepo.save(etlColumn);
            }
            log.info("table columns created for tid {}, total {} columns", etlTable.getId(), columnCount);
            jourService.successJour(etlJour);
            return true;
        }
        catch (SQLException e) {
            jourService.failJour(etlJour, e.getMessage());
            log.error("failed to create table columns for tid {}", etlTable.getId(), e);
            return false;
        }
    }

    private static EtlColumn getEtlColumn(VwEtlTableWithSource etlTable, int i, ResultSetMetaData metaData, Connection connection, Map<String, String> hiveTypeMapping)
            throws SQLException
    {
        EtlColumn etlColumn = new EtlColumn();
        etlColumn.setTid(etlTable.getId());
        etlColumn.setColumnId(i);
        etlColumn.setColumnName(metaData.getColumnName(i));
        etlColumn.setSourceType(metaData.getColumnTypeName(i));
        etlColumn.setDataLength(metaData.getColumnDisplaySize(i));
        etlColumn.setDataPrecision(metaData.getPrecision(i));
        etlColumn.setDataScale(metaData.getScale(i));
        String colComment = DbUtil.getColumnComment(connection, etlTable.getSourceDb(), etlTable.getSourceTable(), metaData.getColumnName(i));
        etlColumn.setColComment(colComment);
        String hiveType = hiveTypeMapping.getOrDefault(metaData.getColumnTypeName(i), "string");
        etlColumn.setTargetType(hiveType);
        if (Objects.equals(hiveType, "decimal")) {
            hiveType = String.format("decimal(%d,%d)", metaData.getPrecision(i), metaData.getScale(i));
        }
        etlColumn.setTargetTypeFull(hiveType);
        return etlColumn;
    }

    /**
     * 获取指定采集表的Hive列信息并转换为DDL语句
     * @param tid 采集表ID
     * @return DDL语句列表
     */
    public List<String> getHiveColumnsAsDDL(Long tid) {
        List<String> result = new ArrayList<>();
        List<EtlColumn> columns = getColumns(tid);
        for (EtlColumn col : columns) {
            String colName ;
            if (isDeletedPlaceholder(col.getColumnName())) {
                colName = col.getColumnName().substring(DELETED_PLACEHOLDER_PREFIX.length());
            } else {
                colName = col.getColumnName();
            }
            if (col.getColComment().isEmpty()) {
                result.add(colName + " " +  col.getTargetTypeFull());
            } else {
                result.add(colName + " " +  col.getTargetTypeFull() + " COMMENT '" + nvlStr(col.getColComment()) + "'");
            }
        }
        return result;
    }

    /**
     * 根据表ID删除对应的字段信息
     * @param tableId 表ID
     */
    public void deleteByTid(long tableId)
    {
        etlColumnRepo.deleteAllByTid(tableId);
    }
}
