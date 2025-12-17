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

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.wgzhao.addax.admin.common.Constants.*;

/**
 * 采集表字段信息服务类，负责采集表字段的增删改查及同步等业务操作
 */
@Service
@Slf4j
@AllArgsConstructor
public class ColumnService {
    private final EtlColumnRepo etlColumnRepo;
    private final DictService dictService;
    private final EtlJourService jourService;
    private final SchemaChangeLogService schemaChangeLogService;

    /**
     * 获取指定采集表的所有字段信息
     *
     * @param tid 采集表ID
     * @return 字段列表
     */
    public List<EtlColumn> getColumns(long tid) {
        return etlColumnRepo.findAllByTidOrderByColumnId(tid);
    }

    /**
     * 新版字段更新逻辑：按名称匹配而非按位置对齐
     * 规则：
     * 1) 遍历目标表现有列，按列名在源表查找：
     * - 存在且类型相同：不变
     * - 存在但类型不同：更新目标列类型（及长度/精度/注释），记录到 schema change 日志
     * - 不存在：视为源端删除该列，目标列名加删除占位前缀，记录删除日志
     * 2) 遍历源表，其余未匹配到的列统一追加到目标表最后，记录新增日志
     * 保持目标表中旧列的相对顺序不变；新增列总是追加到末尾。
     *
     * @param etlTable 采集表视图对象
     * @return 0 表示无需更新, 1 表示字段有更新, -1 表示更新失败
     */
    @Transactional
    public int updateTableColumnsV2(VwEtlTableWithSource etlTable) {
        if (etlTable == null) {
            return 0;
        }
        List<EtlColumn> existingColumns = etlColumnRepo.findAllByTidOrderByColumnId(etlTable.getId());
        if (existingColumns == null || existingColumns.isEmpty()) {
            // 第一次创建，直接全量写入
            log.info("[V2] no existing columns for tid {}, creating all", etlTable.getId());
            return createTableColumns(etlTable) ? 1 : -1;
        }
        log.info("[V2] updating table columns for tid {}.{} ({})", etlTable.getSourceDb(), etlTable.getSourceTable(), etlTable.getId());
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_COLUMN, null);

        boolean changed = false;

        try (Connection connection = DbUtil.getConnection(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass())) {
            assert connection != null;
            List<EtlColumn> sourceCols = getEtlColumnV2(connection, etlTable);
            if (sourceCols == null) {
                return -1;
            }
            // name -> source column
            HashMap<String, EtlColumn> srcByName = new HashMap<>(Math.max(16, sourceCols.size() * 2));
            for (EtlColumn sc : sourceCols) {
                srcByName.put(sc.getColumnName(), sc);
            }
            // 记录已匹配的源列名
            java.util.HashSet<String> matchedNames = new java.util.HashSet<>();

            // 第一阶段：遍历目标表现有列
            for (EtlColumn oc : existingColumns) {
                // 跳过已删除占位的历史列
                if (isDeletedPlaceholder(oc.getColumnName())) {
                    continue;
                }
                EtlColumn sc = srcByName.get(oc.getColumnName());
                if (sc != null) {
                    matchedNames.add(sc.getColumnName());
                    boolean typeChanged = !Objects.equals(oc.getSourceType(), sc.getSourceType())
                            || notEq(oc.getDataPrecision(), sc.getDataPrecision())
                            || notEq(oc.getDataScale(), sc.getDataScale())
                            || oc.getDataLength() != sc.getDataLength();
                    boolean commentChanged = !Objects.equals(nvlStr(oc.getColComment()), nvlStr(sc.getColComment()));
                    if (typeChanged || commentChanged) {
                        // 记录类型变化
                        schemaChangeLogService.recordTypeChange(
                                etlTable.getId(), etlTable.getSourceDb(), etlTable.getSourceTable(), sc.getColumnName(),
                                oc.getSourceType(), sc.getSourceType(),
                                oc.getDataLength(), sc.getDataLength(),
                                oc.getDataPrecision(), sc.getDataPrecision(),
                                oc.getDataScale(), sc.getDataScale(),
                                oc.getColComment(), sc.getColComment());
                        // 同步更新字段信息（源类型、长度/精度/比例、注释、目标类型）
                        oc.setSourceType(sc.getSourceType());
                        oc.setDataLength(sc.getDataLength());
                        oc.setDataPrecision(sc.getDataPrecision());
                        oc.setDataScale(sc.getDataScale());
                        oc.setColComment(sc.getColComment());
                        oc.setTargetType(sc.getTargetType());
                        oc.setTargetTypeFull(sc.getTargetTypeFull());
                        etlColumnRepo.save(oc);
                        changed = true;
                    }
                } else {
                    // 源表中不存在该列 -> 删除占位
                    schemaChangeLogService.recordDelete(
                            etlTable.getId(), etlTable.getSourceDb(), etlTable.getSourceTable(), oc.getColumnName(),
                            oc.getSourceType(), oc.getDataLength(), oc.getDataPrecision(), oc.getDataScale(), oc.getColComment());
                    oc.setColumnName(DELETED_PLACEHOLDER_PREFIX + oc.getColumnName());
                    etlColumnRepo.save(oc);
                    changed = true;
                }
            }

            // 第二阶段：追加源端剩余（未匹配）列到目标表末尾
            int nextId = existingColumns.size();
            for (EtlColumn sc : sourceCols) {
                if (!matchedNames.contains(sc.getColumnName())) {
                    EtlColumn nc = new EtlColumn();
                    BeanUtils.copyProperties(sc, nc);
                    nc.setTid(etlTable.getId());
                    nc.setColumnId(++nextId);
                    etlColumnRepo.save(nc);
                    schemaChangeLogService.recordAdd(
                            etlTable.getId(), etlTable.getSourceDb(), etlTable.getSourceTable(), sc.getColumnName(),
                            sc.getSourceType(), sc.getDataLength(), sc.getDataPrecision(), sc.getDataScale(), sc.getColComment());
                    changed = true;
                }
            }
        } catch (SQLException e) {
            jourService.failJour(etlJour, e.getMessage());
            log.error("[V2] failed to update table columns for tid {}", etlTable.getId(), e);
            return -1;
        }

        log.info("[V2] table columns updated for tid {}, changed={}", etlTable.getId(), changed);
        jourService.successJour(etlJour);
        return changed ? 1 : 0;
    }

    private  List<EtlColumn> getEtlColumnV2(Connection connection, VwEtlTableWithSource etlTable) {
        List<EtlColumn> sourceCols = new ArrayList<>();
        Map<String, String> hiveTypeMapping = dictService.getHiveTypeMapping();
        try {
            String catalog = connection.getCatalog().isEmpty() ? etlTable.getSourceDb() : connection.getCatalog();
            String schema = connection.getSchema();
            if (etlTable.getUrl().startsWith("jdbc:mysql://")) {
                // force switch to catalog for MySQL
                catalog = etlTable.getSourceDb();
                schema =null;
            }
            ResultSet rs = connection.getMetaData().getColumns(catalog, schema, etlTable.getSourceTable(), null);
            int idx = 1;
            while (rs.next()) {
                EtlColumn etlColumn = new EtlColumn();
                etlColumn.setTid(etlTable.getId());
                etlColumn.setColumnId(idx);
                etlColumn.setColumnName(rs.getString("COLUMN_NAME"));
                String sourceType = rs.getString("TYPE_NAME").toLowerCase();
                etlColumn.setSourceType(sourceType);
                etlColumn.setDataLength(rs.getInt("COLUMN_SIZE"));
                etlColumn.setDataPrecision(rs.getInt("COLUMN_SIZE"));
                etlColumn.setDataScale(rs.getInt("DECIMAL_DIGITS"));
                etlColumn.setColComment(rs.getString("REMARKS"));
                String hiveType = hiveTypeMapping.getOrDefault(sourceType, "string");
                etlColumn.setTargetType(hiveType);
                if (Objects.equals(hiveType, "decimal")) {
                    hiveType = String.format("decimal(%d,%d)", rs.getInt("COLUMN_SIZE"), rs.getInt("DECIMAL_DIGITS"));
                }
                etlColumn.setTargetTypeFull(hiveType);
                sourceCols.add(etlColumn);
                idx++;
            }
            return sourceCols;
        } catch (SQLException e) {
            log.error("Failed to get table metadata for {}.{}: {}", etlTable.getSourceDb(), etlTable.getSourceTable(), e.getMessage());
            return null;
        }
    }

    private static boolean isDeletedPlaceholder(String name) {
        return name != null && name.startsWith(DELETED_PLACEHOLDER_PREFIX);
    }

    private static boolean notEq(Integer a, Integer b) {
        return !Objects.equals(a, b);
    }

    private static String nvlStr(String v) {
        return v == null ? "" : v;
    }

    /**
     * 当新增采集表时，添加表的字段信息到 etl_column 表，他包含了源表的字段信息和目标表的字段信息
     *
     * @param etlTable etl_table 表记录
     * @return true 成功，false 失败
     */
    @Transactional
    public boolean createTableColumns(VwEtlTableWithSource etlTable) {
        if (etlTable == null) {
            return false;
        }
        log.info("first add table columns for tid {}.{} ({})", etlTable.getSourceDb(), etlTable.getSourceTable(), etlTable.getId());

        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.CREATE_COLUMN, null);

        try (Connection connection = DbUtil.getConnection(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass())) {
            assert connection != null;
            List<EtlColumn> etlColumns = getEtlColumnV2(connection, etlTable);
            if (etlColumns == null || etlColumns.isEmpty()) {
                jourService.failJour(etlJour, "failed to get source table metadata");
                return false;
            }
            etlColumnRepo.saveAll(etlColumns);

            log.info("table columns created for tid {}, total {} columns", etlTable.getId(), etlColumns.size());
            jourService.successJour(etlJour);
            return true;

        } catch (SQLException e) {
            jourService.failJour(etlJour, e.getMessage());
            log.error("failed to create table columns for tid {}", etlTable.getId(), e);
            return false;
        }
    }

    /**
     * 获取指定采集表的Hive列信息并转换为DDL语句
     *
     * @param tid 采集表ID
     * @return DDL语句列表
     */
    public List<String> getHiveColumnsAsDDL(Long tid) {
        List<String> result = new ArrayList<>();
        List<EtlColumn> columns = getColumns(tid);
        for (EtlColumn col : columns) {
            String colName;
            if (isDeletedPlaceholder(col.getColumnName())) {
                colName = col.getColumnName().substring(DELETED_PLACEHOLDER_PREFIX.length());
            } else {
                colName = col.getColumnName();
            }
            String comment = nvlStr(col.getColComment());
            if (comment.isEmpty()) {
                result.add("`" + colName + "` " + col.getTargetTypeFull());
            } else {
                // Normalize whitespace/newlines and escape single quotes for SQL string literal
                comment = comment.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
                comment = comment.replace("'", "''");
                result.add("`" + colName + "` " + col.getTargetTypeFull() + " COMMENT '" + comment + "'");
            }
        }
        return result;
    }

    /**
     * 根据表ID删除对应的字段信息
     *
     * @param tableId 表ID
     */
    public void deleteByTid(long tableId) {
        etlColumnRepo.deleteAllByTid(tableId);
    }
}
