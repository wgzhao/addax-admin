package com.wgzhao.addax.admin.alert;

import com.wgzhao.addax.admin.alert.dto.AlertRuleDto;
import com.wgzhao.addax.admin.alert.dto.AlertRuleRequest;
import com.wgzhao.addax.admin.alert.model.AlertRule;
import com.wgzhao.addax.admin.alert.repo.AlertChannelRepo;
import com.wgzhao.addax.admin.alert.repo.AlertRuleRepo;
import com.wgzhao.addax.admin.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AlertRuleService
{
    private static final Set<String> SCOPE_TYPES = Set.of("ALL", "SOURCE", "TABLE");

    private final AlertRuleRepo ruleRepo;
    private final AlertChannelRepo channelRepo;

    public List<AlertRuleDto> list()
    {
        return ruleRepo.findAllByOrderByIdAsc().stream().map(this::toDto).toList();
    }

    public AlertRuleDto get(long id)
    {
        return toDto(requireRule(id));
    }

    public AlertRule requireRule(long id)
    {
        return ruleRepo.findById(id)
            .orElseThrow(() -> new ApiException(404, "告警规则不存在: " + id));
    }

    @Transactional
    public AlertRuleDto create(AlertRuleRequest request)
    {
        AlertRule rule = new AlertRule();
        apply(rule, request, true);
        ruleRepo.save(rule);
        return toDto(rule);
    }

    @Transactional
    public AlertRuleDto update(long id, AlertRuleRequest request)
    {
        AlertRule rule = requireRule(id);
        apply(rule, request, false);
        ruleRepo.save(rule);
        return toDto(rule);
    }

    @Transactional
    public void delete(long id)
    {
        ruleRepo.delete(requireRule(id));
    }

    private void apply(AlertRule rule, AlertRuleRequest request, boolean creating)
    {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new ApiException(400, "规则名称不能为空");
        }
        if (name.length() > 100) {
            throw new ApiException(400, "规则名称不能超过 100 个字符");
        }
        AlertEventType eventType;
        try {
            eventType = AlertEventType.valueOf(request.eventType() == null
                ? "" : request.eventType().trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex) {
            throw new ApiException(400, "不支持的事件类型: " + request.eventType());
        }
        AlertSeverity severity = AlertSeverity.parse(request.severity(), eventType.defaultSeverity());
        String scopeType = request.scopeType() == null ? "ALL" : request.scopeType().trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_TYPES.contains(scopeType)) {
            throw new ApiException(400, "规则范围只支持 ALL/SOURCE/TABLE");
        }
        String scopeValue = request.scopeValue() == null ? null : request.scopeValue().trim();
        if ("ALL".equals(scopeType)) {
            scopeValue = null;
        }
        else if (scopeValue == null || scopeValue.isEmpty()) {
            throw new ApiException(400, "范围不是 ALL 时必须填写范围值");
        }
        if (scopeValue != null && scopeValue.length() > 255) {
            throw new ApiException(400, "范围值不能超过 255 个字符");
        }
        List<Long> channelIds = normalizeChannelIds(request.channelIds());
        List<Long> escalation = normalizeChannelIds(request.escalationChannelIds());
        int cooldown = request.cooldownSecs() == null ? 300 : request.cooldownSecs();
        int groupWindow = request.groupWindowSecs() == null ? 60 : request.groupWindowSecs();
        if (cooldown < 0 || cooldown > 86400) {
            throw new ApiException(400, "冷却时间必须在 0-86400 秒之间");
        }
        if (groupWindow < 0 || groupWindow > 86400) {
            throw new ApiException(400, "聚合窗口必须在 0-86400 秒之间");
        }
        if (request.escalateAfterSecs() != null && request.escalateAfterSecs() < 0) {
            throw new ApiException(400, "升级等待时间不能为负数");
        }

        rule.setName(name);
        rule.setEventType(eventType.name());
        rule.setSeverity(severity.name());
        rule.setScopeType(scopeType);
        rule.setScopeValue(scopeValue);
        rule.setChannelIds(channelIds);
        rule.setCooldownSecs(cooldown);
        rule.setGroupWindowSecs(groupWindow);
        rule.setAckRequired(Boolean.TRUE.equals(request.ackRequired()));
        rule.setEscalateAfterSecs(request.escalateAfterSecs());
        rule.setEscalationChannelIds(escalation);
        if (creating || request.enabled() != null) {
            rule.setEnabled(request.enabled() == null || request.enabled());
        }
    }

    private List<Long> normalizeChannelIds(List<Long> requested)
    {
        if (requested == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : requested) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (!unique.isEmpty()) {
            Set<Long> existing = new LinkedHashSet<>();
            channelRepo.findAllById(unique).forEach(channel -> existing.add(channel.getId()));
            List<Long> missing = unique.stream().filter(id -> !existing.contains(id)).toList();
            if (!missing.isEmpty()) {
                throw new ApiException(400, "通道不存在: " + missing);
            }
        }
        return new ArrayList<>(unique);
    }

    private AlertRuleDto toDto(AlertRule rule)
    {
        return new AlertRuleDto(rule.getId(), rule.getName(), rule.getEventType(), rule.getSeverity(),
            rule.getScopeType(), rule.getScopeValue(), rule.getChannelIds(), rule.getCooldownSecs(),
            rule.getGroupWindowSecs(), rule.isAckRequired(), rule.getEscalateAfterSecs(),
            rule.getEscalationChannelIds(), rule.isEnabled(), rule.getCreatedAt(), rule.getUpdatedAt());
    }
}
