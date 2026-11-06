package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.dto.ManualSourceColumnRequest;
import com.wgzhao.addax.admin.dto.SourceFieldMeta;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import com.wgzhao.addax.admin.service.collection.IdentifierPolicy;
import com.wgzhao.addax.admin.service.source.ColumnTypeMapping;
import com.wgzhao.addax.admin.service.source.SchemaMode;
import com.wgzhao.addax.admin.service.source.SourceAccessException;
import com.wgzhao.addax.admin.service.source.SourceAdapter;
import com.wgzhao.addax.admin.service.source.SourceAdapterRegistry;
import com.wgzhao.addax.admin.service.source.SourceField;
import com.wgzhao.addax.admin.service.source.SourceRef;
import com.wgzhao.addax.admin.service.source.SourceSchema;
import com.wgzhao.addax.admin.service.source.TargetFamily;
import com.wgzhao.addax.admin.service.source.TypePurpose;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final ColumnMappingService columnMappingService;
    private final EtlJourService jourService;
    private final SourceAdapterRegistry sourceAdapterRegistry;
    private final RiskLogService riskLogService;
    private final VwEtlTableWithSourceRepo viewRepo;
    private final SchemaChangeLogService schemaChangeLogService;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;

    // Shared with TableService: a transient source outage must not be handled like a schema failure.
    public static final int UPDATE_NO_CHANGE = 0;
    public static final int UPDATE_CHANGED = 1;
    public static final int UPDATE_FAILED = -1;
    public static final int UPDATE_SOURCE_UNAVAILABLE = -2;

    private static boolean isDeletedPlaceholder(String name)
    {
        return name != null && name.startsWith(DELETED_PLACEHOLDER_PREFIX);
    }

    private static String activeColumnName(String name)
    {
        return isDeletedPlaceholder(name) ? name.substring(DELETED_PLACEHOLDER_PREFIX.length()) : name;
    }

    /**
     * Whether a column with this name already exists physically, deleted placeholders included.
     */
    public static boolean hasColumn(List<EtlColumn> columns, String name)
    {
        return matches(columns, name, true);
    }

    /**
     * Whether an active (not a deleted placeholder) column with this name exists, i.e. the source still
     * provides the value.
     */
    public static boolean hasActiveColumn(List<EtlColumn> columns, String name)
    {
        return matches(columns, name, false);
    }

    /**
     * A target must inject a business date literal only when the source does not already carry a column
     * with that name. The reader (which injects the literal) and the writer (which appends the matching
     * column) both go through this predicate so the positional reader→writer mapping cannot drift.
     */
    public static boolean needsInjectedBusinessDate(List<EtlColumn> columns, String businessDateColumn)
    {
        return businessDateColumn != null && !businessDateColumn.isBlank()
            && !hasColumn(columns, businessDateColumn.trim());
    }

    private static boolean matches(List<EtlColumn> columns, String name, boolean includeDeleted)
    {
        if (columns == null || name == null || name.isBlank()) {
            return false;
        }
        for (EtlColumn column : columns) {
            if (!includeDeleted && isDeletedPlaceholder(column.getColumnName())) {
                continue;
            }
            if (name.equalsIgnoreCase(activeColumnName(column.getColumnName()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean notEq(Integer a, Integer b)
    {
        return !Objects.equals(a, b);
    }

    private static String nvlStr(String v)
    {
        return v == null ? "" : v;
    }

    // 判断新的 Hive 目标类型是否兼容（能够表示）已有的目标类型。
    // existingTarget / newTarget 期望为 hive 映射后的类型名（如 int, double, decimal, string）
    private static boolean isHiveTypeCompatible(String existingTarget, String newTarget, EtlColumn oc, EtlColumn sc)
    {
        if (existingTarget == null || newTarget == null) {
            return false;
        }
        existingTarget = existingTarget.toLowerCase();
        newTarget = newTarget.toLowerCase();
        if (existingTarget.equals(newTarget)) {
            return true;
        }
        // string 视为最宽泛，可兼容任何类型
        if ("string".equals(newTarget)) {
            return true;
        }
        // 如果现有是 string，而新类型不是 string，则不兼容（可能丢失信息）
        if ("string".equals(existingTarget)) {
            return false;
        }

        // 数值类型顺序：越后面表示范围越大（更宽泛）
        Map<String, Integer> rank = new HashMap<>();
        // boolean 视为最窄的类型（rank=0），以支持 boolean -> int/double/decimal 的兼容判断
        rank.put("boolean", 0);
        rank.put("tinyint", 1);
        rank.put("smallint", 2);
        rank.put("int", 3);
        rank.put("bigint", 4);
        rank.put("float", 5);
        rank.put("double", 6);

        Integer rExisting = rank.get(existingTarget);
        Integer rNew = rank.get(newTarget);
        if (rExisting != null && rNew != null) {
            return rNew >= rExisting;
        }

        // 处理 decimal 的兼容性
        if (newTarget.startsWith("decimal")) {
            // 新类型为 decimal，通常能兼容普通数值类型
            if (rExisting != null) {
                return true;
            }
            if (existingTarget.startsWith("decimal")) {
                int[] ex = parseDecimalPrecisionScale(oc.getTargetTypeFull());
                int[] nw = parseDecimalPrecisionScale(sc.getTargetTypeFull());
                if (ex != null && nw != null) {
                    // 若新 decimal 的 precision/scale 都 >= 旧的，则兼容
                    return nw[0] >= ex[0] && nw[1] >= ex[1];
                }
                return false;
            }
            return true;
        }

        // 如果现有是 decimal 且新类型不是 decimal，则通常不兼容（可能丢精度）
        if (existingTarget.startsWith("decimal") && !newTarget.startsWith("decimal")) {
            return false;
        }

        // 其它未明确的类型组合，采取保守策略认为不兼容
        return false;
    }

    private static int[] parseDecimalPrecisionScale(String typeFull)
    {
        if (typeFull == null) {
            return null;
        }
        String s = typeFull.toLowerCase();
        int start = s.indexOf("decimal(");
        if (start < 0) {
            return null;
        }
        int l = s.indexOf('(', start);
        int r = s.indexOf(')', l);
        if (l < 0 || r < 0 || r <= l + 1) {
            return null;
        }
        String inside = s.substring(l + 1, r);
        String[] parts = inside.split(",");
        try {
            int p = Integer.parseInt(parts[0].trim());
            int sc = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            return new int[] {p, sc};
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取指定采集表的所有字段信息
     *
     * @param tid 采集表 ID
     * @return 字段列表
     */
    public List<EtlColumn> getColumns(long tid)
    {
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
     * @return 0 表示无需更新, 1 表示字段有更新, -1 表示更新失败,
     *         -2 表示源库暂时不可达（可重试，不应标记为采集失败）
     */
    @Transactional
    public int updateTableColumnsV2(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null) {
            return UPDATE_NO_CHANGE;
        }
        List<EtlColumn> existingColumns = etlColumnRepo.findAllByTidOrderByColumnId(etlTable.getId());
        if (existingColumns == null || existingColumns.isEmpty()) {
            // 第一次创建，直接全量写入
            log.info("[V2] no existing columns for tid {}, creating all", etlTable.getId());
            return createTableColumnsInternal(etlTable);
        }
        log.info("[V2] updating table columns for tid {}.{} ({})", etlTable.getSourceDb(), etlTable.getSourceTable(), etlTable.getId());
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_COLUMN, null);

        boolean changed = false;

        try {
            // 注意，这里的获取到的表字段类型是已经通过目标类型映射过后的，而不是原始字段类型
            // 比如原始类型是 varchar(255)，但映射后是 string
            List<EtlColumn> sourceCols = discoverColumns(etlTable);
            if (sourceCols == null || sourceCols.isEmpty()) {
                // metadata query failed (network, credentials, table dropped)
                // or the table has no visible columns: alert and skip. An
                // empty list must not be treated as "all columns deleted",
                // which would placeholder-delete every existing target column.
                alertAndSkipFetch(etlTable, etlJour, "更新", "获取源表字段失败或源表无字段");
                return UPDATE_FAILED;
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
                    // 如果源类型不同，则进一步用目标 Hive 类型判断兼容性：
                    // 当新的 Hive 类型能够覆盖旧的 Hive 类型表达的值范围时，视为兼容，不认为类型变化
                    boolean sourceTypeDifferent = !Objects.equals(oc.getSourceType(), sc.getSourceType());
                    boolean precisionChanged = notEq(oc.getDataPrecision(), sc.getDataPrecision())
                        || notEq(oc.getDataScale(), sc.getDataScale())
                        || oc.getDataLength() != sc.getDataLength();
                    boolean typeChanged;
                    if (sourceTypeDifferent) {
                        String existingTarget = oc.getTargetType();
                        String newTarget = sc.getTargetType();
                        typeChanged = !isHiveTypeCompatible(existingTarget, newTarget, oc, sc);
                    }
                    else {
                        typeChanged = precisionChanged;
                    }
                    boolean commentChanged = !Objects.equals(
                        nvlStr(oc.getColComment()), nvlStr(sc.getColComment()));
                    boolean sourceMetaChanged = !Objects.equals(oc.getSourceMeta(), sc.getSourceMeta());
                    if (typeChanged) {
                        // 记录类型变化
                        schemaChangeLogService.recordTypeChange(
                            etlTable.getId(), etlTable.getSourceDb(), etlTable.getSourceTable(), sc.getColumnName(),
                            oc.getSourceType(), sc.getSourceType(),
                            oc.getDataLength(), sc.getDataLength(),
                            oc.getDataPrecision(), sc.getDataPrecision(),
                            oc.getDataScale(), sc.getDataScale(),
                            oc.getColComment(), sc.getColComment());
                        // 同步更新字段信息（源类型、长度/精度/比例、注释、目标类型、采样证据）
                        oc.setSourceType(sc.getSourceType());
                        oc.setDataLength(sc.getDataLength());
                        oc.setDataPrecision(sc.getDataPrecision());
                        oc.setDataScale(sc.getDataScale());
                        oc.setColComment(sc.getColComment());
                        oc.setSourceMeta(sc.getSourceMeta());
                        oc.setTargetType(sc.getTargetType());
                        oc.setTargetTypeFull(sc.getTargetTypeFull());
                        etlColumnRepo.save(oc);
                        changed = true;
                    }
                    else {
                        // Sampling evidence changes on every refresh; persist it even when the target
                        // type does not move, but only mark the table changed when the COMMENT does.
                        boolean saved = false;
                        if (commentChanged) {
                            oc.setColComment(sc.getColComment());
                            saved = true;
                        }
                        if (sourceMetaChanged) {
                            oc.setSourceMeta(sc.getSourceMeta());
                            saved = true;
                        }
                        if (saved) {
                            etlColumnRepo.save(oc);
                        }
                        if (commentChanged) {
                            changed = true;
                        }
                    }
                }
                else {
                    if (!sourceAdapterRegistry.resolve(etlTable).capabilities().autoDeleteMissingColumns())
                    {
                        // A schema-on-read sample is not a schema: a rare field can be absent from
                        // this refresh without having been dropped. Keep the existing mapping.
                        riskLogService.recordRisk("ColumnService", "WARN",
                            "源字段 " + oc.getColumnName() + " 未出现在本次采样中，已保留原映射",
                            etlTable.getId());
                        continue;
                    }
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
        }
        catch (SourceAccessException e) {
            if (e.isUnavailable()) {
                alertAndSkipFetch(etlTable, etlJour, "更新", "无法连接源数据库: " + e.getMessage());
                log.error("[V2] failed to connect source for tid {}", etlTable.getId(), e);
                return UPDATE_SOURCE_UNAVAILABLE;
            }
            alertAndSkipFetch(etlTable, etlJour, "更新", e.getMessage());
            log.error("[V2] failed to update table columns for tid {}", etlTable.getId(), e);
            return UPDATE_FAILED;
        }

        log.info("[V2] table columns updated for tid {}, changed={}", etlTable.getId(), changed);
        columnMappingService.reconcile(etlTable.getId());
        jourService.successJour(etlJour);
        return changed ? UPDATE_CHANGED : UPDATE_NO_CHANGE;
    }

    /**
     * Alert on failure to fetch source table columns, mark the jour as failed
     * and skip this round (action: 更新 or 创建).
     */
    private void alertAndSkipFetch(VwEtlTableWithSource etlTable, EtlJour etlJour, String action, String reason)
    {
        String desc = String.format("%s.%s(%d)", etlTable.getSourceDb(), etlTable.getSourceTable(), etlTable.getId());
        alertService.sendToWeComRobot(
            String.format("采集表 %s 字段%s失败，无法获取源表字段，原因：%s，已跳过本次%s", desc, action, reason, action));
        jourService.failJour(etlJour, reason);
        log.error("[V2] skip {} table columns for {}, reason: {}", action, desc, reason);
    }

    /**
     * Declare a source field that sampling cannot see. Used by schema-on-read sources for empty
     * collections, rare fields and type overrides.
     */
    @Transactional
    public List<EtlColumn> addManualColumn(long tid, ManualSourceColumnRequest request)
    {
        VwEtlTableWithSource view = viewRepo.findById(tid)
            .orElseThrow(() -> new ApiException(404, "采集表不存在: " + tid));
        SourceAdapter adapter = sourceAdapterRegistry.resolve(view);
        if (!adapter.capabilities().samplingInference())
        {
            throw new ApiException(400, "该采集源不支持手工添加字段");
        }
        if (request == null || request.sourceName() == null || request.sourceName().isBlank())
        {
            throw new ApiException(400, "源字段名不能为空");
        }
        String sourceName = request.sourceName().trim();
        IdentifierPolicy policy = columnMappingService.sourcePolicy(tid);
        List<EtlColumn> existing = etlColumnRepo.findAllByTidOrderByColumnId(tid);
        for (EtlColumn column : existing)
        {
            if (!isDeletedPlaceholder(column.getColumnName())
                && policy.key(activeColumnName(column.getColumnName())).equals(policy.key(sourceName)))
            {
                throw new ApiException(409, "源字段已存在: " + sourceName);
            }
        }

        String sourceType = request.sourceType() == null || request.sourceType().isBlank()
            ? "string" : request.sourceType().trim().toLowerCase();
        java.util.Set<String> allowed = java.util.Set.of(
            "string", "int", "bigint", "double", "decimal", "boolean", "timestamp", "text", "binary");
        if (!allowed.contains(sourceType))
        {
            throw new ApiException(400, "不支持的源字段类型: " + sourceType);
        }
        int length = request.dataLength() == null || request.dataLength() <= 0
            ? ("string".equals(sourceType) ? 255 : 0) : request.dataLength();
        Integer precision = request.dataPrecision();
        Integer scale = request.dataScale();
        if ("decimal".equals(sourceType))
        {
            precision = precision == null ? 38 : precision;
            scale = scale == null ? 10 : scale;
        }

        TargetFamily family = view.getTargetType() == null || view.getTargetType().isBlank()
            || "HDFS".equalsIgnoreCase(view.getTargetType()) ? TargetFamily.HDFS : TargetFamily.RDBMS;
        DbType targetDialect = DbType.fromValue(view.getTargetType()).orElse(null);
        SourceField field = new SourceField(sourceName, sourceType, sourceType, length, precision, scale,
            request.comment(), true, false, request.targetName(), null);
        ColumnTypeMapping mapping = adapter.mapColumnType(field, family, targetDialect, TypePurpose.ETL_COLUMN);

        int nextId = existing.stream().map(EtlColumn::getColumnId)
            .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        EtlColumn column = new EtlColumn();
        column.setTid(tid);
        column.setColumnId(nextId);
        column.setColumnName(sourceName);
        column.setSourceType(sourceType);
        column.setDataLength(length);
        column.setDataPrecision(precision == null ? 0 : precision);
        column.setDataScale(scale == null ? 0 : scale);
        column.setColComment(request.comment());
        column.setTargetType(mapping.targetType());
        column.setTargetTypeFull(mapping.targetTypeFull());
        etlColumnRepo.save(column);
        columnMappingService.reconcile(tid);
        columnMappingService.setTargetName(tid, sourceName, request.targetName());
        return etlColumnRepo.findAllByTidOrderByColumnId(tid);
    }

    private List<EtlColumn> discoverColumns(VwEtlTableWithSource etlTable)
    {
        SourceAdapter adapter = sourceAdapterRegistry.resolve(etlTable);
        SourceSchema schema = adapter.inspectSchema(SourceRef.of(etlTable), etlTable.getSourceDb(),
            etlTable.getSourceTable(), SchemaMode.COLUMN_REFRESH);
        TargetFamily family = etlTable.getTargetType() == null
            || etlTable.getTargetType().isBlank()
            || "HDFS".equalsIgnoreCase(etlTable.getTargetType())
            ? TargetFamily.HDFS : TargetFamily.RDBMS;
        DbType targetDialect = DbType.fromValue(etlTable.getTargetType()).orElse(null);

        List<EtlColumn> sourceCols = new ArrayList<>(schema.fields().size());
        int idx = 1;
        for (SourceField field : schema.fields())
        {
            EtlColumn etlColumn = new EtlColumn();
            etlColumn.setTid(etlTable.getId());
            etlColumn.setColumnId(idx);
            etlColumn.setColumnName(field.name());
            etlColumn.setSourceType(field.sourceType());
            etlColumn.setDataLength(field.dataLength());
            etlColumn.setDataPrecision(field.dataPrecision());
            etlColumn.setDataScale(field.dataScale());
            etlColumn.setColComment(field.comment());
            etlColumn.setSourceMeta(toJson(field.sourceMeta()));
            ColumnTypeMapping mapping = adapter.mapColumnType(field, family, targetDialect, TypePurpose.ETL_COLUMN);
            etlColumn.setTargetType(mapping.targetType());
            etlColumn.setTargetTypeFull(mapping.targetTypeFull());
            sourceCols.add(etlColumn);
            idx++;
        }
        return sourceCols;
    }

    private JsonNode toJson(SourceFieldMeta meta)
    {
        return meta == null ? null : objectMapper.valueToTree(meta);
    }

    /**
     * 当新增采集表时，添加表的字段信息到 etl_column 表，他包含了源表的字段信息和目标表的字段信息
     *
     * @param etlTable etl_table 表记录
     * @return true 成功，false 失败
     */
    @Transactional
    public boolean createTableColumns(VwEtlTableWithSource etlTable)
    {
        return createTableColumnsInternal(etlTable) == UPDATE_CHANGED;
    }

    private int createTableColumnsInternal(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null) {
            return UPDATE_FAILED;
        }
        log.info("first add table columns for tid {}.{} ({})", etlTable.getSourceDb(), etlTable.getSourceTable(), etlTable.getId());

        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.CREATE_COLUMN, null);

        try {
            List<EtlColumn> etlColumns = discoverColumns(etlTable);
            if (etlColumns == null || etlColumns.isEmpty()) {
                alertAndSkipFetch(etlTable, etlJour, "创建", "获取源表字段失败或源表无字段");
                return UPDATE_FAILED;
            }
            etlColumnRepo.saveAll(etlColumns);
            columnMappingService.reconcile(etlTable.getId());

            log.info("table columns created for tid {}, total {} columns", etlTable.getId(), etlColumns.size());
            jourService.successJour(etlJour);
            return UPDATE_CHANGED;
        }
        catch (SourceAccessException e) {
            if (e.isUnavailable()) {
                alertAndSkipFetch(etlTable, etlJour, "创建", "无法连接源数据库: " + e.getMessage());
                log.error("failed to connect source for tid {}", etlTable.getId(), e);
                return UPDATE_SOURCE_UNAVAILABLE;
            }
            alertAndSkipFetch(etlTable, etlJour, "创建", e.getMessage());
            log.error("failed to create table columns for tid {}", etlTable.getId(), e);
            return UPDATE_FAILED;
        }
    }

    /**
     * 获取指定采集表的 Hive 列信息并转换为DDL语句
     *
     * @param tid 采集表 ID
     * @return DDL 语句列表
     */
    public List<String> getHiveColumnsAsDDL(Long tid)
    {
        List<String> result = new ArrayList<>();
        for (ColumnMappingService.ResolvedColumnMapping mapping : columnMappingService.resolve(tid)) {
            EtlColumn col = mapping.sourceColumn();
            if (col == null || mapping.targetColumnName() == null || mapping.targetColumnName().isBlank()) {
                continue;
            }
            String colName = mapping.targetColumnName().replace("`", "``");
            String comment = nvlStr(col.getColComment());
            if (comment.isEmpty()) {
                result.add("`" + colName + "` " + col.getTargetTypeFull());
            }
            else {
                // Normalize whitespace/newlines and escape single quotes for SQL string literal
                comment = comment.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
                comment = comment.replace("'", "''");
                result.add("`" + colName + "` " + col.getTargetTypeFull() + " COMMENT '" + comment + "'");
            }
        }
        return result;
    }

    /**
     * 根据表 ID 删除对应的字段信息
     *
     * @param tableId 表ID
     */
    public void deleteByTid(long tableId)
    {
        etlColumnRepo.deleteAllByTid(tableId);
    }
}
