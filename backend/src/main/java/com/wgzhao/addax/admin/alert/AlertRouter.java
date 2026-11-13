package com.wgzhao.addax.admin.alert;

import tools.jackson.databind.JsonNode;
import com.wgzhao.addax.admin.alert.model.AlertChannel;
import com.wgzhao.addax.admin.alert.model.AlertEvent;
import com.wgzhao.addax.admin.alert.model.AlertRule;
import com.wgzhao.addax.admin.alert.repo.AlertChannelRepo;
import com.wgzhao.addax.admin.alert.repo.AlertRuleRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves which enabled channels an event reaches.
 *
 * <p>A matching enabled rule selects its channel list. When no rule matches, the
 * event goes to every enabled channel: a channel-only installation still alerts,
 * and rules are an explicit narrowing mechanism rather than a second switch an
 * operator can forget. This default is shown in the alert configuration UI.</p>
 */
@Component
@RequiredArgsConstructor
public class AlertRouter
{
    private final AlertRuleRepo ruleRepo;
    private final AlertChannelRepo channelRepo;

    public RouteResult route(AlertEvent event)
    {
        Map<Long, AlertChannel> enabled = channelRepo.findByEnabledTrueOrderByIdAsc().stream()
            .collect(Collectors.toMap(AlertChannel::getId, Function.identity(), (a, b) -> a));
        List<AlertRule> matched = ruleRepo.findByEnabledTrueOrderByIdAsc().stream()
            .filter(rule -> matches(rule, event))
            .toList();
        if (matched.isEmpty()) {
            return new RouteResult(new ArrayList<>(enabled.keySet()),
                AlertSeverity.parse(event.getSeverity(), AlertSeverity.P3), false);
        }
        LinkedHashSet<Long> channelIds = new LinkedHashSet<>();
        AlertSeverity severity = null;
        for (AlertRule rule : matched) {
            severity = AlertSeverity.mostSevere(severity,
                AlertSeverity.parse(rule.getSeverity(), AlertSeverity.P3));
            if (rule.getChannelIds() != null) {
                for (Long channelId : rule.getChannelIds()) {
                    if (channelId != null && enabled.containsKey(channelId)) {
                        channelIds.add(channelId);
                    }
                }
            }
        }
        return new RouteResult(new ArrayList<>(channelIds),
            severity == null ? AlertSeverity.parse(event.getSeverity(), AlertSeverity.P3) : severity, true);
    }

    boolean matches(AlertRule rule, AlertEvent event)
    {
        if (!rule.getEventType().equalsIgnoreCase(event.getEventType())) {
            return false;
        }
        String scopeType = rule.getScopeType() == null ? "ALL" : rule.getScopeType();
        String scopeValue = rule.getScopeValue();
        if ("ALL".equals(scopeType)) {
            return true;
        }
        JsonNode data = event.getPayload() == null ? null : event.getPayload().path("data");
        String sourceDb = data == null ? null : textOrNull(data, "sourceDb");
        String sourceTable = data == null ? null : textOrNull(data, "sourceTable");
        if ("SOURCE".equals(scopeType)) {
            if (sourceDb == null && "SOURCE".equalsIgnoreCase(event.getScopeType())) {
                sourceDb = event.getScopeValue();
            }
            return sourceDb != null && sourceDb.equalsIgnoreCase(scopeValue);
        }
        if ("TABLE".equals(scopeType)) {
            if (scopeValue == null) {
                return false;
            }
            if (scopeValue.equals(event.getScopeValue())) {
                return true;
            }
            if (sourceDb != null && sourceTable != null
                && scopeValue.equalsIgnoreCase(sourceDb + "." + sourceTable)) {
                return true;
            }
            if (sourceTable != null && scopeValue.equalsIgnoreCase(sourceTable)) {
                return true;
            }
        }
        return false;
    }

    private String textOrNull(JsonNode node, String field)
    {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    public record RouteResult(List<Long> channelIds, AlertSeverity severity, boolean matchedRule)
    {
    }
}
