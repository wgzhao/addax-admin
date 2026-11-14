package com.wgzhao.addax.admin.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.dto.EtlTargetView;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.service.target.RdbmsWriterPlugins;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import com.wgzhao.addax.admin.service.target.TargetAdapterRegistry;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.wgzhao.addax.admin.common.Constants.HDFS_TARGET_TYPES;
import static com.wgzhao.addax.admin.common.Constants.RDBMS_TARGET_TYPES;

@Service
@RequiredArgsConstructor
@Slf4j
public class EtlTargetService
{
    /**
     * Writer defaults the add-table flow needs. Everything else in connect_config can identify the
     * cluster or contain a credential, so it is stripped for non-admin readers.
     */
    private static final Set<String> PUBLIC_CONNECT_KEYS =
        Set.of("defaultFS", "hdfsPrefix", "storageFormat", "compressFormat");

    private final EtlTargetRepo etlTargetRepo;
    private final EtlTableRepo etlTableRepo;
    private final TargetAdapterRegistry targetAdapterRegistry;
    private final RdbmsWriterPlugins rdbmsWriterPlugins;
    private final ObjectMapper objectMapper;

    public List<EtlTarget> listAll(Boolean enabledOnly)
    {
        if (Boolean.TRUE.equals(enabledOnly)) {
            return etlTargetRepo.findByEnabledTrueOrderByIsDefaultDescIdAsc();
        }
        return etlTargetRepo.findAllByOrderByIsDefaultDescIdAsc();
    }

    public EtlTarget getById(Long id)
    {
        return etlTargetRepo.findById(id)
            .orElseThrow(() -> new ApiException(404, "Target not found"));
    }

    public List<EtlTargetView> listViews(Boolean enabledOnly, boolean includeSecrets)
    {
        return listAll(enabledOnly).stream()
            .map(target -> toView(target, includeSecrets))
            .toList();
    }

    public EtlTargetView getView(Long id, boolean includeSecrets)
    {
        return toView(getById(id), includeSecrets);
    }

    private EtlTargetView toView(EtlTarget target, boolean includeSecrets)
    {
        return EtlTargetView.from(target, includeSecrets
            ? target.getConnectConfig()
            : sanitizeConnectConfig(target.getConnectConfig()));
    }

    private String sanitizeConnectConfig(String raw)
    {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            if (!parsed.isObject()) {
                return "{}";
            }
            ObjectNode sanitized = objectMapper.createObjectNode();
            for (String key : PUBLIC_CONNECT_KEYS) {
                JsonNode value = parsed.get(key);
                if (value != null) {
                    sanitized.set(key, value);
                }
            }
            return objectMapper.writeValueAsString(sanitized);
        }
        catch (Exception e) {
            log.warn("Failed to sanitize target connect config: {}", e.getMessage());
            return "{}";
        }
    }

    @Transactional
    public EtlTarget create(EtlTarget target)
    {
        validate(target, true);
        EtlTarget saved = etlTargetRepo.save(target);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            etlTargetRepo.clearDefaultExcept(saved.getId());
        }
        return saved;
    }

    @Transactional
    public EtlTarget update(Long id, EtlTarget target)
    {
        EtlTarget existed = getById(id);
        existed.setCode(target.getCode());
        existed.setName(target.getName());
        existed.setTargetType(target.getTargetType());
        existed.setConnectConfig(target.getConnectConfig());
        existed.setWriterTemplateKey(target.getWriterTemplateKey());
        existed.setEnabled(target.getEnabled());
        existed.setIsDefault(target.getIsDefault());
        existed.setRemark(target.getRemark());
        validate(existed, false);
        etlTargetRepo.findByCode(existed.getCode()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ApiException(409, "Target code already exists");
            }
        });
        EtlTarget saved = etlTargetRepo.save(existed);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            etlTargetRepo.clearDefaultExcept(saved.getId());
        }
        return saved;
    }

    public void delete(Long id)
    {
        EtlTarget existed = getById(id);
        if (etlTableRepo.countByTargetId(id) > 0)
        {
            throw new ApiException(400, "Target is in use by collection tables and cannot be deleted");
        }
        try {
            etlTargetRepo.delete(existed);
        }
        catch (DataIntegrityViolationException e) {
            throw new ApiException(400, "Target is in use and cannot be deleted");
        }
    }

    public void testConnect(Long id)
    {
        EtlTarget target = getById(id);
        doTestConnect(target);
    }

    /**
     * Resolve the target family and concrete RDBMS dialect from a JDBC URL, without opening a
     * connection. The family follows the same rule the routing resolver uses (Hive URL -> HDFS),
     * and the dialect's writer plugin reflects whether the dialect is implemented end to end.
     * The save-time gate in {@code RdbmsTargetAdapter#validateForSave} remains authoritative.
     */
    public DialectInfo detectDialect(String url)
    {
        if (url == null || url.isBlank())
        {
            throw new ApiException(400, "JDBC URL is required");
        }
        DbType dialect = DbUtil.getDbType(url.trim());
        if (HDFS_TARGET_TYPES.contains(dialect.name()))
        {
            return new DialectInfo("HDFS", dialect.name(), "hdfswriter", true, "");
        }
        boolean supported = rdbmsWriterPlugins.isSupported(dialect);
        return new DialectInfo("RDBMS", dialect.name(),
            supported ? rdbmsWriterPlugins.pluginName(dialect) : null,
            supported, rdbmsWriterPlugins.supportedDialectsText());
    }

    public record DialectInfo(String family, String dialect, String writerPlugin,
        boolean supported, String supportedDialects)
    {
    }

    public void testConnect(EtlTarget target)
    {
        doTestConnect(target);
    }

    private void doTestConnect(EtlTarget target)
    {
        if (target == null)
        {
            throw new ApiException(400, "Target payload is empty");
        }
        TargetAdapter adapter = resolveAdapter(target);
        try
        {
            adapter.testConnection(target);
        }
        catch (IllegalStateException e)
        {
            throw new ApiException(400, e.getMessage());
        }
    }

    private void validate(EtlTarget target, boolean checkCodeConflict)
    {
        if (target == null) {
            throw new ApiException(400, "Target payload is empty");
        }
        if (target.getCode() == null || target.getCode().isBlank()) {
            throw new ApiException(400, "Target code is required");
        }
        if (target.getName() == null || target.getName().isBlank()) {
            throw new ApiException(400, "Target name is required");
        }
        if (target.getTargetType() == null || target.getTargetType().isBlank()) {
            throw new ApiException(400, "Target type is required");
        }
        target.setTargetType(normalizeType(target.getTargetType()));
        target.setWriterTemplateKey(inferWriterKey(target));
        if (target.getEnabled() == null) {
            target.setEnabled(true);
        }
        if (target.getIsDefault() == null) {
            target.setIsDefault(false);
        }
        TargetAdapter adapter = resolveAdapter(target);
        try
        {
            adapter.validateForSave(target);
        }
        catch (IllegalStateException e)
        {
            throw new ApiException(400, e.getMessage());
        }
        if (checkCodeConflict && etlTargetRepo.findByCode(target.getCode()).isPresent()) {
            throw new ApiException(409, "Target code already exists");
        }
    }

    private TargetAdapter resolveAdapter(EtlTarget target)
    {
        try
        {
            return targetAdapterRegistry.resolveForTarget(target);
        }
        catch (IllegalStateException e)
        {
            throw new ApiException(400, e.getMessage());
        }
    }

    /**
     * Keep API clients that only send target_type working: the writer key is routing metadata and can
     * be derived from the family. An explicit key always wins.
     */
    private String inferWriterKey(EtlTarget target)
    {
        String key = target.getWriterTemplateKey();
        if (key != null && !key.isBlank())
        {
            return key.trim();
        }
        String type = normalizeType(target.getTargetType());
        if (HDFS_TARGET_TYPES.contains(type))
        {
            return "wH";
        }
        if (RDBMS_TARGET_TYPES.contains(type) || DbType.fromValue(type).isPresent())
        {
            return "wR";
        }
        return key;
    }

    private String normalizeType(String targetType)
    {
        if (targetType == null || targetType.isBlank()) {
            return "";
        }
        return targetType.trim().toUpperCase(Locale.ROOT);
    }
}
