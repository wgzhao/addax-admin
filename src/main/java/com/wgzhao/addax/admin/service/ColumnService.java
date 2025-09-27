package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;

/**
 * 采集表字段信息管理
 */
@Service
@Slf4j
@AllArgsConstructor
public class ColumnService
{
    private final EtlColumnRepo etlColumnRepo;
    private final DictService dictService;

    public List<EtlColumn> getColumns(long tid) {
        return etlColumnRepo.findAllByTidOrderByColumnId(tid);
    }

    /**
     * 更新当前表的字段信息，主要是涉及到源表字段的变更
     * 这里表字段变更修改的逻辑是：
     * 1. 假定如果表有新增字段，那一定是增加在最后，而不是在中间。
     * 2. 如果源表字段进行了删除，那么我们应该把源表字段名字设置为一个特定的名字( __deleted__），
     *      而目标表字段不变动，这样做的原因是因为 hive 表每日都有采集，如果直接删除一个字段，那意味着之前采集的数据都无法使用
     * 3. 如果源表字段的类型进行了更新，那就同步更新源表字段类型和目标表字段类型，并记录本次变更到风险表，用来提醒用户注意变化
     * 4. 如果新增新增字段，则直接在增加字段记录即可。
     *
     * @param etlTable etl_table 表记录
     * @return 0 表示无需更新, 1 表示字段有更新，-1 表示更新失败
     */
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
        // 获取源表的字段信息
        Connection connection = DbUtil.getConnect(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass());
        if (connection == null) {
            log.warn("failed to get connection for source {}", etlTable.getUrl());
            return -1;
        }

        Map<String, String> hiveTypeMapping = dictService.getHiveTypeMapping();
        String sql = "select * from `" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "` where 1=0";

        boolean changed = false;

        try (ResultSet rs = connection.createStatement().executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();

            // 构造源端列信息列表（保持顺序）
            List<EtlColumn> sourceCols = new ArrayList<>(n);
            for (int i = 1; i <= n; i++) {
                EtlColumn sc = new EtlColumn();
                sc.setTid(etlTable.getId());
                sc.setColumnId(i);
                sc.setColumnName(md.getColumnName(i));
                sc.setSourceType(md.getColumnTypeName(i));
                sc.setDataLength(md.getColumnDisplaySize(i));
                sc.setDataPrecision(md.getPrecision(i));
                sc.setDataScale(md.getScale(i));
                String colComment = DbUtil.getColumnComment(connection, etlTable.getSourceDb(), etlTable.getSourceTable(), md.getColumnName(i));
                sc.setColComment(colComment);
                String hiveType = hiveTypeMapping.getOrDefault(sc.getSourceType(), "string");
                sc.setTargetType(hiveType);
                if (Objects.equals(hiveType, "decimal")) {
                    hiveType = String.format("decimal(%d,%d)",
                            sc.getDataPrecision() == null ? 0 : sc.getDataPrecision(),
                            sc.getDataScale() == null ? 0 : sc.getDataScale());
                }
                sc.setTargetTypeFull(hiveType);
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
                EtlColumn sc = sourceCols.get(s);
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
                EtlColumn sc = sourceCols.get(s++);
                EtlColumn nc = new EtlColumn();
                nc.setTid(sc.getTid());
                nc.setColumnId(++nextId);
                nc.setColumnName(sc.getColumnName());
                nc.setSourceType(sc.getSourceType());
                nc.setDataLength(sc.getDataLength());
                nc.setDataPrecision(sc.getDataPrecision());
                nc.setDataScale(sc.getDataScale());
                nc.setColComment(sc.getColComment());
                nc.setTargetType(sc.getTargetType());
                nc.setTargetTypeFull(sc.getTargetTypeFull());
                etlColumnRepo.save(nc);
                changed = true;
            }
        }
        catch (SQLException e) {
            log.error("failed to update table columns for tid {}", etlTable.getId(), e);
            return -1;
        }
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
    public boolean createTableColumns(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null) {
            return false;
        }
        Connection connection = DbUtil.getConnect(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass());
        if (connection == null) {
            log.warn("cannot get connection for id {}", etlTable.getUrl());
            return false;
        }
        Map<String, String> hiveTypeMapping = dictService.getHiveTypeMapping();
        String sql = "select * from `" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "` where 1=0";
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
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
                etlColumnRepo.save(etlColumn);
            }
            return true;
        }
        catch (SQLException e) {
            log.error("failed to create table columns for tid {}", etlTable.getId(), e);
            return false;
        }
    }

    public List<String> getHiveColumns(Long tid) {
        List<String> result = new ArrayList<>();
        List<EtlColumn> columns = getColumns(tid);
        for (EtlColumn col : columns) {
            String colName ;
            if (isDeletedPlaceholder(col.getColumnName())) {
                colName = col.getColumnName().substring(DELETED_PLACEHOLDER_PREFIX.length());
            } else {
                colName = col.getColumnName();
            }
            result.add("{\"name\":\"" + colName + "\", \"type\":\"" + col.getTargetTypeFull() + "\"}");
        }
        return result;
    }
}
