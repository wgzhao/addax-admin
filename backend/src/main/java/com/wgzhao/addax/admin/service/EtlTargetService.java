package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.dto.DbConnectDto;
import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import static com.wgzhao.addax.admin.common.Constants.DEFAULT_TARGET_TYPE;

@Service
@RequiredArgsConstructor
@Slf4j
public class EtlTargetService
{
    private final EtlTargetRepo etlTargetRepo;
    private final TargetService targetService;
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

    public void testConnect(EtlTarget target)
    {
        validate(target, false);
        doTestConnect(target);
    }

    private void doTestConnect(EtlTarget target)
    {
        String writerKey = target.getWriterTemplateKey() == null ? "" : target.getWriterTemplateKey().trim().toUpperCase(Locale.ROOT);
        if ("WH".equals(writerKey)) {
            HiveConnectDto hiveConnectDto = parseHiveConnect(target.getConnectConfig());
            try {
                Connection connection = targetService.getHiveDataSourceWithConfig(hiveConnectDto).getConnection();
                connection.close();
            }
            catch (SQLException | MalformedURLException e) {
                throw new ApiException(400, "Failed to connect target: " + e.getMessage());
            }
            return;
        }

        DbConnectDto dbConnectDto = parseDbConnect(target.getConnectConfig());
        if (!DbUtil.testConnection(dbConnectDto.url(), dbConnectDto.username(), dbConnectDto.password())) {
            throw new ApiException(400, "Failed to connect target");
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
            target.setTargetType(DEFAULT_TARGET_TYPE);
        }
        target.setTargetType(normalizeType(target.getTargetType()));
        if (target.getEnabled() == null) {
            target.setEnabled(true);
        }
        if (target.getIsDefault() == null) {
            target.setIsDefault(false);
        }
        if (checkCodeConflict && etlTargetRepo.findByCode(target.getCode()).isPresent()) {
            throw new ApiException(409, "Target code already exists");
        }
    }

    private String normalizeType(String targetType)
    {
        if (targetType == null || targetType.isBlank()) {
            return DEFAULT_TARGET_TYPE;
        }
        return targetType.trim().toUpperCase(Locale.ROOT);
    }

    private HiveConnectDto parseHiveConnect(String config)
    {
        JsonNode node = parseConfigNode(config);
        return new HiveConnectDto(
            getText(node, "url"),
            getText(node, "username"),
            getText(node, "password"),
            getText(node, "driverClassName"),
            getText(node, "driverPath")
        );
    }

    private DbConnectDto parseDbConnect(String config)
    {
        JsonNode node = parseConfigNode(config);
        DbConnectDto dto = new DbConnectDto(
            getText(node, "url"),
            getText(node, "username"),
            getText(node, "password")
        );
        if (dto.url() == null || dto.url().isBlank()) {
            throw new ApiException(400, "connectConfig.url is required");
        }
        return dto;
    }

    private JsonNode parseConfigNode(String config)
    {
        if (config == null || config.isBlank()) {
            throw new ApiException(400, "connectConfig is required");
        }
        try {
            return objectMapper.readTree(config);
        }
        catch (Exception e) {
            throw new ApiException(400, "Invalid connectConfig JSON");
        }
    }

    private String getText(JsonNode node, String key)
    {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? "" : v.asText();
    }
}
