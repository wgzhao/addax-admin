package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.ColumnMappingDto;
import com.wgzhao.addax.admin.dto.ColumnMappingInput;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlColumnMapping;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlColumnMappingRepo;
import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import com.wgzhao.addax.admin.service.collection.IdentifierPolicy;
import com.wgzhao.addax.admin.service.source.SourceAdapterRegistry;
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
    private final VwEtlTableWithSourceRepo viewRepo;
    private final SourceAdapterRegistry sourceAdapterRegistry;

    public List<ResolvedColumnMapping> resolve(long tid)
    {
        List<EtlColumn> sourceColumns = columnRepo.findAllByTidOrderByColumnId(tid);
        List<EtlColumnMapping> mappings = mappingRepo.findAllByTidOrderByOrdinalNoAscIdAsc(tid);
        if (mappings.isEmpty())
        {
            mappings = identityMappings(tid, sourceColumns);
        }
        IdentifierPolicy sourcePolicy = sourcePolicy(tid);

        Map<String, EtlColumn> bySource = new HashMap<>();
        for (EtlColumn column : sourceColumns)
        {
            String sourceName = activeName(column.getColumnName());
            if (sourceName == null || sourceName.isBlank())
            {
                continue;
            }
            // A dropped column and its re-added twin share one active name. The live row must win,
            // otherwise the mapping is reported inactive and the reader emits NULL for a real column.
            String mapKey = sourcePolicy.key(sourceName);
            EtlColumn existing = bySource.get(mapKey);
            if (existing == null || (isDeleted(existing.getColumnName()) && !isDeleted(column.getColumnName())))
            {
                bySource.put(mapKey, column);
            }
        }

        List<ResolvedColumnMapping> resolved = new ArrayList<>();
        for (EtlColumnMapping mapping : mappings)
        {
            EtlColumn column = bySource.get(sourcePolicy.key(mapping.getSourceColumnName()));
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
        IdentifierPolicy sourcePolicy = sourcePolicy(tid);
        Map<String, EtlColumnMapping> existing = new LinkedHashMap<>();
        Set<String> usedTargets = new HashSet<>();
        for (EtlColumnMapping mapping : mappings)
        {
            existing.put(sourcePolicy.key(mapping.getSourceColumnName()), mapping);
            usedTargets.add(key(mapping.getTargetColumnName()));
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
            String mapKey = sourcePolicy.key(sourceName);
            if (existing.containsKey(mapKey))
            {
                continue;
            }
            EtlColumnMapping mapping = new EtlColumnMapping();
            mapping.setTid(tid);
            mapping.setSourceColumnName(sourceName);
            mapping.setTargetColumnName(suggestedTargetName(sourcePolicy, sourceName, usedTargets));
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

        IdentifierPolicy sourcePolicy = sourcePolicy(tid);
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
            if (!sources.add(sourcePolicy.key(source)))
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
        // Legacy tables can carry foo and __deleted__foo at once. Keep one identity mapping per
        // active name and prefer the live row, matching the startup backfill.
        IdentifierPolicy sourcePolicy = sourcePolicy(tid);
        Map<String, EtlColumn> columnsByName = new LinkedHashMap<>();
        for (EtlColumn column : sourceColumns)
        {
            String sourceName = activeName(column.getColumnName());
            if (sourceName == null || sourceName.isBlank())
            {
                continue;
            }
            String mapKey = sourcePolicy.key(sourceName);
            EtlColumn existing = columnsByName.get(mapKey);
            if (existing == null || (isDeleted(existing.getColumnName()) && !isDeleted(column.getColumnName())))
            {
                columnsByName.put(mapKey, column);
            }
        }

        List<EtlColumnMapping> result = new ArrayList<>();
        Set<String> usedTargets = new HashSet<>();
        int ordinal = 0;
        for (EtlColumn column : columnsByName.values())
        {
            String sourceName = activeName(column.getColumnName()).trim();
            EtlColumnMapping mapping = new EtlColumnMapping();
            mapping.setTid(tid);
            mapping.setSourceColumnName(sourceName);
            mapping.setTargetColumnName(suggestedTargetName(sourcePolicy, sourceName, usedTargets));
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
            source == null ? null : source.getSourceMeta(),
            mapping.updatedAt());
    }

    /**
     * Source-name comparison rule for a table. MongoDB fields are case-sensitive, RDBMS names keep the
     * historical insensitive behavior.
     */
    public IdentifierPolicy sourcePolicy(long tid)
    {
        VwEtlTableWithSource view = viewRepo.findById(tid).orElse(null);
        if (view == null)
        {
            return IdentifierPolicy.INSENSITIVE;
        }
        try
        {
            return sourceAdapterRegistry.resolve(view).capabilities().caseSensitiveIdentifiers()
                ? IdentifierPolicy.EXACT : IdentifierPolicy.INSENSITIVE;
        }
        catch (RuntimeException e)
        {
            return IdentifierPolicy.INSENSITIVE;
        }
    }

    public String sourceKey(long tid, String value)
    {
        return sourcePolicy(tid).key(value);
    }

    @Transactional
    public void setTargetName(long tid, String sourceName, String targetName)
    {
        if (targetName == null || targetName.isBlank())
        {
            return;
        }
        IdentifierPolicy policy = sourcePolicy(tid);
        List<EtlColumnMapping> mappings = mappingRepo.findAllByTidOrderByOrdinalNoAscIdAsc(tid);
        String targetKey = key(targetName);
        for (EtlColumnMapping mapping : mappings)
        {
            if (policy.key(mapping.getSourceColumnName()).equals(policy.key(sourceName)))
            {
                for (EtlColumnMapping other : mappings)
                {
                    if (!other.getId().equals(mapping.getId()) && key(other.getTargetColumnName()).equals(targetKey))
                    {
                        throw new ApiException(409, "目标字段已存在: " + targetName);
                    }
                }
                mapping.setTargetColumnName(targetName.trim());
                mappingRepo.save(mapping);
                return;
            }
        }
    }

    private static String suggestedTargetName(IdentifierPolicy policy, String sourceName,
        Set<String> usedTargets)
    {
        String base = policy == IdentifierPolicy.EXACT ? sanitizeTargetName(sourceName) : sourceName;
        String candidate = base;
        int suffix = 2;
        while (!usedTargets.add(key(candidate)))
        {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    /**
     * A MongoDB dotted path cannot be an RDBMS column name. Keep the path readable and avoid
     * characters the target dialect would reject; the user can still rename it in the review step.
     */
    private static String sanitizeTargetName(String sourceName)
    {
        String name = sourceName.replace(".", "__").replaceAll("[^A-Za-z0-9_$]", "_");
        if (name.isEmpty() || (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_'))
        {
            name = "_" + name;
        }
        return name.length() > 120 ? name.substring(0, 120) : name;
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
