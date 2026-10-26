package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.ColumnMappingDto;
import com.wgzhao.addax.admin.dto.ColumnMappingInput;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlColumnMapping;
import com.wgzhao.addax.admin.repository.EtlColumnMappingRepo;
import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;

/**
 * Owns the stable source-to-target column contract. Source schema discovery stays in etl_column;
 * callers use this module instead of inferring target names from source names.
 */
@Service
@RequiredArgsConstructor
public class ColumnMappingService
{
    private final EtlColumnMappingRepo mappingRepo;
    private final EtlColumnRepo columnRepo;

    public List<ResolvedColumnMapping> resolve(long tid)
    {
        List<EtlColumn> sourceColumns = columnRepo.findAllByTidOrderByColumnId(tid);
        List<EtlColumnMapping> mappings = mappingRepo.findAllByTidOrderByOrdinalNoAscIdAsc(tid);
        if (mappings.isEmpty())
        {
            mappings = identityMappings(tid, sourceColumns);
        }

        Map<String, EtlColumn> bySource = new HashMap<>();
        for (EtlColumn column : sourceColumns)
        {
            bySource.put(key(activeName(column.getColumnName())), column);
        }

        List<ResolvedColumnMapping> resolved = new ArrayList<>();
        for (EtlColumnMapping mapping : mappings)
        {
            EtlColumn column = bySource.get(key(mapping.getSourceColumnName()));
            resolved.add(new ResolvedColumnMapping(
                mapping.getId(),
                mapping.getSourceColumnName(),
                mapping.getTargetColumnName(),
                mapping.getOrdinalNo(),
                column != null && !isDeleted(column.getColumnName()),
                column,
                mapping.getUpdatedAt()));
        }
        return resolved;
    }

    public List<ColumnMappingDto> list(long tid)
    {
        return resolve(tid).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public List<ColumnMappingDto> reconcile(long tid)
    {
        List<EtlColumn> sourceColumns = columnRepo.findAllByTidOrderByColumnId(tid);
        List<EtlColumnMapping> mappings = mappingRepo.findAllByTidOrderByOrdinalNoAscIdAsc(tid);
        Map<String, EtlColumnMapping> existing = new LinkedHashMap<>();
        for (EtlColumnMapping mapping : mappings)
        {
            existing.put(key(mapping.getSourceColumnName()), mapping);
        }

        int nextOrdinal = mappings.stream()
            .map(EtlColumnMapping::getOrdinalNo)
            .filter(value -> value != null)
            .max(Integer::compareTo)
            .orElse(0) + 1;
        List<EtlColumnMapping> created = new ArrayList<>();
        for (EtlColumn column : sourceColumns)
        {
            String sourceName = activeName(column.getColumnName());
            if (sourceName == null || sourceName.isBlank() || isDeleted(column.getColumnName()))
            {
                continue;
            }
            String mapKey = key(sourceName);
            if (existing.containsKey(mapKey))
            {
                continue;
            }
            EtlColumnMapping mapping = new EtlColumnMapping();
            mapping.setTid(tid);
            mapping.setSourceColumnName(sourceName);
            mapping.setTargetColumnName(sourceName);
            mapping.setOrdinalNo(nextOrdinal++);
            created.add(mapping);
        }
        if (!created.isEmpty())
        {
            mappingRepo.saveAll(created);
        }
        return list(tid);
    }

    @Transactional
    public List<ColumnMappingDto> replace(long tid, List<ColumnMappingInput> inputs)
    {
        if (inputs == null || inputs.isEmpty())
        {
            throw new ApiException(400, "At least one column mapping is required");
        }

        List<EtlColumnMapping> replacements = new ArrayList<>();
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        int ordinal = 0;
        for (ColumnMappingInput input : inputs)
        {
            String source = trim(input.sourceColumnName());
            String target = trim(input.targetColumnName());
            if (source == null || target == null)
            {
                throw new ApiException(400, "Source and target column names are required");
            }
            if (!sources.add(key(source)))
            {
                throw new ApiException(409, "Duplicate source column mapping: " + source);
            }
            if (!targets.add(key(target)))
            {
                throw new ApiException(409, "Multiple source columns map to target column: " + target);
            }

            EtlColumnMapping mapping = new EtlColumnMapping();
            mapping.setTid(tid);
            mapping.setSourceColumnName(source);
            mapping.setTargetColumnName(target);
            mapping.setOrdinalNo(input.ordinalNo() == null ? ++ordinal : input.ordinalNo());
            replacements.add(mapping);
        }

        mappingRepo.deleteAllByTid(tid);
        mappingRepo.flush();
        mappingRepo.saveAll(replacements);
        return list(tid);
    }

    public static boolean isDeleted(String name)
    {
        return name != null && name.startsWith(DELETED_PLACEHOLDER_PREFIX);
    }

    public static String activeName(String name)
    {
        return isDeleted(name) ? name.substring(DELETED_PLACEHOLDER_PREFIX.length()) : name;
    }

    private List<EtlColumnMapping> identityMappings(long tid, List<EtlColumn> sourceColumns)
    {
        List<EtlColumnMapping> result = new ArrayList<>();
        int ordinal = 0;
        for (EtlColumn column : sourceColumns)
        {
            String sourceName = activeName(column.getColumnName());
            if (sourceName == null || sourceName.isBlank())
            {
                continue;
            }
            EtlColumnMapping mapping = new EtlColumnMapping();
            mapping.setTid(tid);
            mapping.setSourceColumnName(sourceName);
            mapping.setTargetColumnName(sourceName);
            mapping.setOrdinalNo(++ordinal);
            result.add(mapping);
        }
        return result;
    }

    private ColumnMappingDto toDto(ResolvedColumnMapping mapping)
    {
        EtlColumn source = mapping.sourceColumn();
        return new ColumnMappingDto(
            mapping.id(),
            mapping.sourceColumnName(),
            mapping.targetColumnName(),
            mapping.ordinalNo(),
            mapping.sourceActive(),
            source == null ? null : source.getSourceType(),
            source == null ? null : source.getTargetType(),
            source == null ? null : source.getTargetTypeFull(),
            source == null ? null : source.getColComment(),
            mapping.updatedAt());
    }

    private static String key(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trim(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ResolvedColumnMapping(
        Long id,
        String sourceColumnName,
        String targetColumnName,
        Integer ordinalNo,
        boolean sourceActive,
        EtlColumn sourceColumn,
        java.time.LocalDateTime updatedAt
    )
    {
    }
}
