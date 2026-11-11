package com.wgzhao.addax.admin.service.source.es;

import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.source.ColumnTypeMapping;
import com.wgzhao.addax.admin.service.source.SourceField;
import com.wgzhao.addax.admin.common.TargetFamily;
import com.wgzhao.addax.admin.service.source.TypePurpose;
import com.wgzhao.addax.admin.utils.RdbmsDdlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Maps the portable types produced by {@link EsSchemaInspector} to the target engine's spelling.
 *
 * <p>Dates are intentionally strings here: {@code _source} preserves the original date text and the
 * Addax reader emits a StringColumn, so a target timestamp column would make the writer fail.
 * Object and array values are JSON text for the same reason.</p>
 */
@Component
@RequiredArgsConstructor
public class EsTypeMapper
{
    private static final int VARCHAR_CAP = 16383;

    private final DictService dictService;

    public ColumnTypeMapping map(SourceField field, TargetFamily family, DbType dialect,
        TypePurpose purpose)
    {
        String type = field.sourceType() == null ? "string" : field.sourceType().toLowerCase(Locale.ROOT);
        if (family == TargetFamily.HDFS)
        {
            return hive(type, field);
        }
        return rdbms(type, field, dialect == null ? DbType.RDBMS : dialect, purpose);
    }

    private ColumnTypeMapping hive(String type, SourceField field)
    {
        if ("boolean".equals(type))
        {
            return new ColumnTypeMapping("boolean", "boolean");
        }
        if ("binary".equals(type))
        {
            return new ColumnTypeMapping("binary", "binary");
        }
        if ("bigint".equals(type))
        {
            // ES long is exactly signed 64-bit. The global Hive dictionary is tuned for RDBMS
            // sources and may narrow bigint to decimal; an ES long must keep the full range.
            return new ColumnTypeMapping("bigint", "bigint");
        }
        if ("decimal".equals(type))
        {
            int precision = field.dataPrecision() == null ? 38 : field.dataPrecision();
            int scale = field.dataScale() == null ? 10 : field.dataScale();
            String mapped = "decimal(" + precision + "," + scale + ")";
            return new ColumnTypeMapping(mapped, mapped);
        }
        Map<String, String> mapping = dictService.getHiveTypeMapping();
        String mapped = mapping.getOrDefault(type, "string");
        return new ColumnTypeMapping(mapped, mapped);
    }

    private ColumnTypeMapping rdbms(String type, SourceField field, DbType dialect, TypePurpose purpose)
    {
        String canonical;
        String full;
        switch (type)
        {
            case "int":
                canonical = "int";
                full = RdbmsDdlUtil.renderType(dialect, "int", 0, 0, 0);
                break;
            case "bigint":
                canonical = "bigint";
                full = RdbmsDdlUtil.renderType(dialect, "bigint", 0, 0, 0);
                break;
            case "double":
                canonical = "double";
                full = RdbmsDdlUtil.renderType(dialect, "double", 0, 0, 0);
                break;
            case "decimal":
                canonical = "decimal";
                full = RdbmsDdlUtil.renderType(dialect, "decimal", 0, 38, 10);
                break;
            case "boolean":
                canonical = "boolean";
                full = RdbmsDdlUtil.renderType(dialect, "boolean", 0, 0, 0);
                break;
            case "timestamp":
                canonical = "timestamp";
                full = timestampType(dialect);
                break;
            case "binary":
                canonical = "binary";
                full = RdbmsDdlUtil.renderType(dialect, "binary", 0, 0, 0);
                break;
            case "string":
            case "text":
            default:
            {
                int length = field.dataLength() > 0 ? field.dataLength() : 255;
                if (length > VARCHAR_CAP || "text".equals(type))
                {
                    canonical = "text";
                    full = RdbmsDdlUtil.renderType(dialect, "text", 0, 0, 0);
                }
                else
                {
                    canonical = "varchar";
                    full = RdbmsDdlUtil.renderType(dialect, "varchar", length, 0, 0);
                }
                break;
            }
        }
        if (purpose == TypePurpose.PLANNED_DDL)
        {
            return new ColumnTypeMapping(full, full);
        }
        return new ColumnTypeMapping(canonical, full);
    }

    private static String timestampType(DbType dialect)
    {
        return switch (dialect)
        {
            case MYSQL -> "datetime(3)";
            case SQLSERVER -> "datetime2(3)";
            default -> "timestamp(3)";
        };
    }
}
