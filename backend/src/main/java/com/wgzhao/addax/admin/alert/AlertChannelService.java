package com.wgzhao.addax.admin.alert;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.alert.dto.AlertChannelDto;
import com.wgzhao.addax.admin.alert.dto.AlertChannelRequest;
import com.wgzhao.addax.admin.alert.dto.AlertChannelTestResult;
import com.wgzhao.addax.admin.alert.dto.AlertSummaryDto;
import com.wgzhao.addax.admin.alert.model.AlertChannel;
import com.wgzhao.addax.admin.alert.model.AlertRule;
import com.wgzhao.addax.admin.alert.repo.AlertChannelRepo;
import com.wgzhao.addax.admin.alert.repo.AlertRuleRepo;
import com.wgzhao.addax.admin.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertChannelService
{
    private final AlertChannelRepo channelRepo;
    private final AlertRuleRepo ruleRepo;
    private final AlertSecretCipher cipher;
    private final AlertConfigMasker masker;
    private final AlertProviderRegistry providerRegistry;
    private final AlertMessageFactory messageFactory;
    private final ObjectMapper objectMapper;

    public List<AlertChannelDto> list()
    {
        return channelRepo.findAllByOrderByIdAsc().stream().map(this::toDto).toList();
    }

    public AlertSummaryDto summary()
    {
        long total = channelRepo.count();
        long enabled = channelRepo.countByEnabledTrue();
        return new AlertSummaryDto(total, enabled, enabled == 0);
    }

    public AlertChannelDto get(long id)
    {
        return toDto(requireChannel(id));
    }

    public AlertChannel requireChannel(long id)
    {
        return channelRepo.findById(id)
            .orElseThrow(() -> new ApiException(404, "告警通道不存在: " + id));
    }

    @Transactional
    public AlertChannelDto create(AlertChannelRequest request, String username)
    {
        String name = requireName(request.name());
        AlertChannelType type = parseType(request.type());
        JsonNode config = requireConfig(request.config());
        if (containsMask(config)) {
            throw new ApiException(400, "新增通道不能使用已脱敏的配置，请填写真实值");
        }
        validateConfig(type, config);
        if (channelRepo.existsByNameIgnoreCase(name)) {
            throw new ApiException(409, "告警通道名称已存在: " + name);
        }
        if (Boolean.TRUE.equals(request.enabled())) {
            throw new ApiException(400, "新增通道需保存并完成测试发送后才能启用");
        }

        AlertChannel channel = new AlertChannel();
        channel.setName(name);
        channel.setType(type.name());
        channel.setConfigCiphertext(cipher.encrypt(config));
        channel.setEnabled(false);
        channel.setRateLimitPerMinute(normalizeRate(request.rateLimitPerMinute(), type));
        channel.setLastTestOk(false);
        channel.setCreatedBy(username);
        channelRepo.save(channel);
        log.info("Alert channel {} ({}) created by {}", channel.getId(), type, username);
        return toDto(channel);
    }

    @Transactional
    public AlertChannelDto update(long id, AlertChannelRequest request)
    {
        AlertChannel channel = requireChannel(id);
        String name = requireName(request.name());
        if (channelRepo.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(409, "告警通道名称已存在: " + name);
        }
        AlertChannelType type = AlertChannelType.valueOf(channel.getType());
        if (request.type() != null && !request.type().isBlank()
            && !type.name().equalsIgnoreCase(request.type().trim())) {
            throw new ApiException(400, "通道类型创建后不可修改，请新建通道");
        }

        JsonNode existing = decryptConfig(channel);
        JsonNode merged = masker.mergeMasked(requireConfig(request.config()), existing);
        validateConfig(type, merged);
        boolean configChanged = !Objects.equals(merged, existing);

        channel.setName(name);
        if (configChanged) {
            channel.setConfigCiphertext(cipher.encrypt(merged));
            // A test result belongs to the exact configuration that was tested. An
            // enabled channel whose endpoint changed must stop sending until the new
            // configuration passes a test, otherwise the gate only applies at creation.
            channel.setLastTestOk(false);
            channel.setLastTestAt(null);
            channel.setEnabled(false);
        }
        if (request.rateLimitPerMinute() != null) {
            channel.setRateLimitPerMinute(normalizeRate(request.rateLimitPerMinute(), type));
        }
        if (request.enabled() != null) {
            if (request.enabled() && !Boolean.TRUE.equals(channel.getLastTestOk())) {
                throw new ApiException(400, "请先完成测试发送并确认成功后再启用通道");
            }
            channel.setEnabled(request.enabled());
        }
        channelRepo.save(channel);
        return toDto(channel);
    }

    @Transactional
    public void delete(long id)
    {
        AlertChannel channel = requireChannel(id);
        for (AlertRule rule : ruleRepo.findAll()) {
            boolean changed = false;
            if (rule.getChannelIds() != null && rule.getChannelIds().remove(id)) {
                changed = true;
            }
            if (rule.getEscalationChannelIds() != null && rule.getEscalationChannelIds().remove(id)) {
                changed = true;
            }
            if (changed) {
                ruleRepo.save(rule);
            }
        }
        channelRepo.delete(channel);
        log.info("Alert channel {} ({}) deleted", id, channel.getName());
    }

    /**
     * Sends a synchronous test message. Test results are deliberately kept out of
     * alert_delivery: they are a configuration check, not an alert fact.
     */
    @Transactional
    public AlertChannelTestResult test(long id)
    {
        AlertChannel channel = requireChannel(id);
        JsonNode config = decryptConfig(channel);
        AlertChannelType type = AlertChannelType.valueOf(channel.getType());
        validateConfig(type, config);
        AlertChannelProvider provider = providerRegistry.get(type);
        AlertMessage message = messageFactory.testMessage(channel.getName(), null);
        AlertDeliveryResult result = provider.send(config, message);
        channel.setLastTestAt(Instant.now());
        channel.setLastTestOk(result.success());
        channelRepo.save(channel);
        return new AlertChannelTestResult(result.success(),
            result.success() ? "测试消息已送达" : (result.error() == null ? "测试消息发送失败" : result.error()),
            result.responseCode(), result.responseBody(), result.latencyMs(), channel.getLastTestAt());
    }

    public JsonNode decryptConfig(AlertChannel channel)
    {
        return cipher.decrypt(channel.getConfigCiphertext());
    }

    public AlertChannelDto toDto(AlertChannel channel)
    {
        JsonNode config;
        try {
            config = masker.mask(decryptConfig(channel));
        }
        catch (ApiException ex) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("_decryptError", ex.getMessage());
            config = error;
        }
        AlertChannelType type = AlertChannelType.valueOf(channel.getType());
        return new AlertChannelDto(channel.getId(), channel.getName(), channel.getType(), type.label(),
            channel.isEnabled(), config, channel.getRateLimitPerMinute(), channel.getLastTestAt(),
            channel.getLastTestOk(), channel.getCreatedBy(), channel.getCreatedAt(), channel.getUpdatedAt());
    }

    private String requireName(String value)
    {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            throw new ApiException(400, "通道名称不能为空");
        }
        if (name.length() > 100) {
            throw new ApiException(400, "通道名称不能超过 100 个字符");
        }
        return name;
    }

    private AlertChannelType parseType(String value)
    {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "通道类型不能为空");
        }
        try {
            return AlertChannelType.valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            throw new ApiException(400, "不支持的通道类型: " + value);
        }
    }

    private JsonNode requireConfig(JsonNode config)
    {
        if (config == null || !config.isObject()) {
            throw new ApiException(400, "通道配置必须是 JSON 对象");
        }
        return config;
    }

    private int normalizeRate(Integer requested, AlertChannelType type)
    {
        int value = requested == null ? type.defaultRateLimitPerMinute() : requested;
        return Math.min(Math.max(value, 1), 600);
    }

    private void validateConfig(AlertChannelType type, JsonNode config)
    {
        try {
            providerRegistry.get(type).validate(config);
        }
        catch (IllegalArgumentException ex) {
            throw new ApiException(400, ex.getMessage());
        }
    }

    private boolean containsMask(JsonNode node)
    {
        if (node == null) {
            return false;
        }
        if (node.isTextual()) {
            return AlertConfigMasker.MASK.equals(node.asText());
        }
        if (node.isContainer()) {
            List<Boolean> found = new ArrayList<>();
            node.forEach(child -> found.add(containsMask(child)));
            return found.contains(Boolean.TRUE);
        }
        return false;
    }
}
