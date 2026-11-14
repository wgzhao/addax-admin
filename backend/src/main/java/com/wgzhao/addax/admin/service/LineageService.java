package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.ColumnLineageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Field-level lineage over the stable {@code etl_column_mapping} contract.
 *
 * <p>ODS collection is a single EL hop, so lineage is one edge per mapping: a source column flows
 * into a target column of one collection definition. No extra table is needed — the mapping rows are
 * the edges, and the table view supplies the source/target identity.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LineageService
{
    private static final int SEARCH_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Global field search: every mapping whose source or target column matches the keyword,
     * with the collection's source and target identity attached.
     */
    public List<ColumnLineageDto> searchColumns(String query)
    {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty())
        {
            return List.of();
        }
        String like = "%" + keyword + "%";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select m.tid,
                   t.name as source_name,
                   t.source_db,
                   t.source_table,
                   m.source_column_name,
                   t.target_name,
                   t.target_db,
                   t.target_table,
                   m.target_column_name
              from etl_column_mapping m
              join vw_etl_table_with_source t on m.tid = t.id
             where t.retired_at is null
               and (m.source_column_name ilike ? or m.target_column_name ilike ?)
             order by m.target_column_name, t.target_table, t.source_table
             limit %d
            """.formatted(SEARCH_LIMIT), like, like);
        return rows.stream().map(this::toDto).toList();
    }

    private ColumnLineageDto toDto(Map<String, Object> row)
    {
        return new ColumnLineageDto(
            ((Number) row.get("tid")).longValue(),
            str(row.get("source_name")),
            str(row.get("source_db")),
            str(row.get("source_table")),
            str(row.get("source_column_name")),
            str(row.get("target_name")),
            str(row.get("target_db")),
            str(row.get("target_table")),
            str(row.get("target_column_name")));
    }

    private static String str(Object value)
    {
        return value == null ? null : value.toString();
    }
}
