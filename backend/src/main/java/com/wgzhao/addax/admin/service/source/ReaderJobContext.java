package com.wgzhao.addax.admin.service.source;

import com.wgzhao.addax.admin.service.ColumnMappingService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything an adapter needs to render a reader fragment except the source endpoint itself.
 *
 * <p>{@code maxValueLookup} is supplied by the common layer because the target adapter owns the MAX
 * query. The source adapter decides which column name to look up: RDBMS keeps the source name,
 * MongoDB uses the mapped target name.</p>
 */
public record ReaderJobContext(
    Map<String, String> dateValues,
    LocalDate lastEtlDate,
    String partValue,
    List<ColumnMappingService.ResolvedColumnMapping> mappings,
    MaxValueLookup maxValueLookup
)
{
    @FunctionalInterface
    public interface MaxValueLookup
    {
        Object lookup(String column, String partValue);
    }
}
