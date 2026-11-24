package com.wgzhao.addax.admin.alert;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.alert.dto.AlertTestEventRequest;
import com.wgzhao.addax.admin.alert.model.AlertChannel;
import com.wgzhao.addax.admin.alert.model.AlertDelivery;
import com.wgzhao.addax.admin.alert.model.AlertEvent;
import com.wgzhao.addax.admin.alert.model.AlertSilence;
import com.wgzhao.addax.admin.alert.repo.AlertChannelRepo;
import com.wgzhao.addax.admin.alert.repo.AlertDeliveryRepo;
import com.wgzhao.addax.admin.alert.repo.AlertEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Turns an alert condition into an alert_event plus one alert_delivery row per
 * routed channel, inside a single transaction. Delivery itself is asynchronous
 * (see {@link AlertDeliveryWorker}), so a slow or broken channel never blocks
 * collection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertDispatcher
{
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private static final List<AlertDeliveryStatus> FIRING_DELIVERY_STATUSES = List.of(
        AlertDeliveryStatus.PENDING, AlertDeliveryStatus.SENDING, AlertDeliveryStatus.SENT,
        AlertDeliveryStatus.FAILED, AlertDeliveryStatus.DEAD);

    private final AlertEventRepo eventRepo;
    private final AlertDeliveryRepo deliveryRepo;
    private final AlertChannelRepo channelRepo;
    private final AlertRouter router;
    private final AlertSilenceService silenceService;
    private final AlertMessageFactory messageFactory;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public boolean publishCollectionFailure(long tid, String sourceDb, String sourceTable,
        String error, int attempt, int maxAttempts)
    {
        String eventKey = "COLLECT_FAILURE:TABLE:" + tid;
        lock(eventKey);
        Instant now = Instant.now();
        Optional<AlertEvent> open = eventRepo.findFirstByEventKeyAndStatusInOrderByIdDesc(
            eventKey, List.of(AlertEventStatus.FIRING, AlertEventStatus.SILENCED));
        if (open.isPresent()) {
            AlertEvent event = open.get();
            event.setLastSeen(now);
            event.setFireCount(event.getFireCount() + 1);
            eventRepo.save(event);
            log.debug("Alert {} already open, fire_count={}", eventKey, event.getFireCount());
            return false;
        }

        String taskDesc = taskDescription(tid, sourceDb, sourceTable);
        String reason = error == null || error.isBlank() ? "未知错误" : truncate(error, 1000);
        String title = "采集失败 · " + taskDesc;
        String markdown = "## <font color=\"red\"> 数据采集失败</font>\n"
            + "**时间**: " + TIME_FORMAT.format(now) + "\n"
            + "**主机**: " + messageFactory.hostname() + "\n"
            + "**任务**: " + taskDesc + "\n"
            + "**尝试**: " + attempt + "/" + maxAttempts + "\n"
            + "---------------------------------\n"
            + "**失败原因**: " + reason;
        String text = "【数据采集失败】" + taskDesc + "，尝试 " + attempt + "/" + maxAttempts + "，原因：" + reason;
        ObjectNode data = objectMapper.createObjectNode();
        data.put("tid", tid);
        data.put("sourceDb", nullToEmpty(sourceDb));
        data.put("sourceTable", nullToEmpty(sourceTable));
        data.put("error", reason);
        data.put("attempt", attempt);
        data.put("maxAttempts", maxAttempts);
        JsonNode payload = messageFactory.payload(title, markdown, text,
            "<h2>" + AlertMessageFactory.escapeHtml(title) + "</h2><p>" + AlertMessageFactory.escapeHtml(reason) + "</p>",
            data, Map.of());
        recordEvent(eventKey, AlertEventType.COLLECT_FAILURE, "TABLE", String.valueOf(tid),
            sourceDb, payload, now, AlertSeverity.P1, false);
        return true;
    }

    /**
     * Resolves the open failure and notifies only the channels the failure
     * actually targeted; a recovery message must never introduce a new audience.
     */
    @Transactional
    public boolean publishCollectionRecovery(long tid, String sourceDb, String sourceTable)
    {
        String failureKey = "COLLECT_FAILURE:TABLE:" + tid;
        lock(failureKey);
        Optional<AlertEvent> open = eventRepo.findFirstByEventKeyAndStatusInOrderByIdDesc(
            failureKey, List.of(AlertEventStatus.FIRING, AlertEventStatus.SILENCED));
        if (open.isEmpty()) {
            return false;
        }
        AlertEvent failure = open.get();
        Instant now = Instant.now();
        failure.setStatus(AlertEventStatus.RESOLVED);
        failure.setResolvedAt(now);
        failure.setLastSeen(now);
        eventRepo.save(failure);

        List<Long> firingChannelIds = deliveryRepo.findChannelIdsByEventId(
            failure.getId(), FIRING_DELIVERY_STATUSES);
        List<AlertChannel> channels = channelRepo.findAllById(firingChannelIds).stream()
            .filter(AlertChannel::isEnabled).toList();

        String taskDesc = taskDescription(tid, sourceDb, sourceTable);
        String title = "采集恢复 · " + taskDesc;
        String markdown = "## <font color=\"green\"> 数据采集恢复</font>\n"
            + "**时间**: " + TIME_FORMAT.format(now) + "\n"
            + "**主机**: " + messageFactory.hostname() + "\n"
            + "**任务**: " + taskDesc + "\n"
            + "**原告警时间**: " + TIME_FORMAT.format(failure.getFirstSeen()) + "\n"
            + "**告警次数**: " + failure.getFireCount() + "\n"
            + "---------------------------------\n"
            + "采集已恢复正常";
        String text = "【数据采集恢复】" + taskDesc + " 已恢复正常";
        ObjectNode data = objectMapper.createObjectNode();
        data.put("tid", tid);
        data.put("sourceDb", nullToEmpty(sourceDb));
        data.put("sourceTable", nullToEmpty(sourceTable));
        data.put("alertedAt", TIME_FORMAT.format(failure.getFirstSeen()));
        data.put("fireCount", failure.getFireCount());
        JsonNode payload = messageFactory.payload(title, markdown, text,
            "<h2>" + AlertMessageFactory.escapeHtml(title) + "</h2><p>采集已恢复正常</p>", data, Map.of());

        AlertEvent recovery = new AlertEvent();
        recovery.setEventKey("COLLECT_RECOVERY:TABLE:" + tid);
        recovery.setEventType(AlertEventType.COLLECT_RECOVERY.name());
        recovery.setSeverity(AlertEventType.COLLECT_RECOVERY.defaultSeverity().name());
        recovery.setScopeType("TABLE");
        recovery.setScopeValue(String.valueOf(tid));
        recovery.setStatus(AlertEventStatus.RESOLVED);
        recovery.setFirstSeen(now);
        recovery.setLastSeen(now);
        recovery.setFireCount(1);
        recovery.setResolvedAt(now);
        recovery.setPayload(payload);
        eventRepo.save(recovery);

        Optional<AlertSilence> silence = silenceService.findActiveFor("TABLE", String.valueOf(tid),
            sourceDb, sourceTable);
        publishDeliveries(recovery, channels.stream().map(AlertChannel::getId).toList(), sourceDb,
            silence.orElse(null), null);
        return true;
    }

    @Transactional
    public boolean publishSecurity(String message)
    {
        return publishGlobal(AlertEventType.SECURITY, "SECURITY:GLOBAL:" + shortHash(message),
            "安全告警 · SECURITY", message, "red", "安全审计检测到需要关注的事件",
            AlertSeverity.P1, Map.of(), false);
    }

    /**
     * Operational messages that are not tied to one collection definition
     * (scheduler failure, calendar exhausted, manual test). They share the same
     * event/outbox path instead of a side-channel webhook call.
     */
    @Transactional
    public boolean publishSystem(String message)
    {
        return publishGlobal(AlertEventType.SYSTEM, "SYSTEM:GLOBAL:" + shortHash(message),
            "系统告警 · SYSTEM", message, "orange", "系统运行检测到需要关注的事件",
            AlertSeverity.P2, Map.of(), false);
    }

    /**
     * Scheduler-domain failure (day-cut refresh, scheduler liveness). The key must be
     * stable for one condition: a retry has to increment fire_count on the same open
     * event instead of creating a new one, so callers pass an explicit suffix rather
     * than letting the message text decide identity.
     */
    @Transactional
    public boolean publishSchedule(String keySuffix, String title, String message,
        AlertSeverity severity, Map<String, String> data)
    {
        return publishGlobal(AlertEventType.SCHEDULE, "SCHEDULE:" + keySuffix, title, message,
            "orange", "调度告警", severity, data, false);
    }

    /**
     * Informational summary of a business day. Two deliberate differences from every
     * failure type: it is stored as a resolved point-in-time fact (it reports what
     * happened, not an ongoing condition), and it is delivered only when an enabled
     * DAILY rule exists. Without that exception a once-a-day message would be pushed
     * to every enabled channel through the default route.
     */
    @Transactional
    public boolean publishDaily(String keySuffix, String title, String message, Map<String, String> data)
    {
        return publishGlobal(AlertEventType.DAILY, "DAILY:" + keySuffix, title, message,
            "blue", "每日汇总", AlertSeverity.P3, data, true);
    }

    /**
     * Resolves open SCHEDULE events whose key starts with {@code prefix}, except the
     * keys named in {@code keepKeys}. A later fully successful refresh supersedes an
     * earlier failure; the old event must not stay FIRING forever. Returned events are
     * the ones just closed, so the caller can find who saw the failure.
     */
    @Transactional
    public List<AlertEvent> resolveOpenScheduleEvents(String prefix, Set<String> keepKeys)
    {
        List<AlertEvent> open = eventRepo.findByEventTypeAndStatusIn(AlertEventType.SCHEDULE.name(),
            List.of(AlertEventStatus.FIRING, AlertEventStatus.SILENCED));
        Instant now = Instant.now();
        List<AlertEvent> resolved = new ArrayList<>();
        for (AlertEvent event : open) {
            if (!event.getEventKey().startsWith(prefix) || keepKeys.contains(event.getEventKey())) {
                continue;
            }
            event.setStatus(AlertEventStatus.RESOLVED);
            event.setResolvedAt(now);
            event.setLastSeen(now);
            eventRepo.save(event);
            resolved.add(event);
        }
        return resolved;
    }

    /**
     * Announces that a schedule condition really recovered. Only the channels that
     * received the failure are targeted, so a recovery never reaches a new audience;
     * a failure that was never delivered anywhere has nobody to notify.
     */
    @Transactional
    public boolean publishScheduleRecovery(String keySuffix, String title, String message,
        Map<String, String> data, List<AlertEvent> failures)
    {
        if (failures == null || failures.isEmpty()) {
            return false;
        }
        Set<Long> channelIds = new LinkedHashSet<>();
        Instant firstSeen = null;
        int fireCount = 0;
        for (AlertEvent failure : failures) {
            channelIds.addAll(deliveryRepo.findChannelIdsByEventId(failure.getId(), FIRING_DELIVERY_STATUSES));
            if (failure.getFirstSeen() != null
                && (firstSeen == null || failure.getFirstSeen().isBefore(firstSeen))) {
                firstSeen = failure.getFirstSeen();
            }
            fireCount += failure.getFireCount();
        }
        List<AlertChannel> channels = channelRepo.findAllById(channelIds).stream()
            .filter(AlertChannel::isEnabled).toList();
        Instant now = Instant.now();
        String body = message == null ? "" : message;
        ObjectNode payloadData = objectMapper.createObjectNode();
        if (data != null) {
            data.forEach(payloadData::put);
        }
        payloadData.put("alertedAt", firstSeen == null ? "" : TIME_FORMAT.format(firstSeen));
        payloadData.put("fireCount", fireCount);
        String markdown = "## <font color=\"green\"> " + title + "</font>\n"
            + "**时间**: " + TIME_FORMAT.format(now) + "\n"
            + "**主机**: " + messageFactory.hostname() + "\n"
            + (firstSeen == null ? "" : "**原失败告警时间**: " + TIME_FORMAT.format(firstSeen) + "\n")
            + "**告警次数**: " + fireCount + "\n"
            + "---------------------------------\n"
            + "**告警内容**: " + body;
        String text = "【" + title + "】" + body;
        JsonNode payload = messageFactory.payload(title, markdown, text,
            "<h2>" + AlertMessageFactory.escapeHtml(title) + "</h2><p>"
                + AlertMessageFactory.escapeHtml(body) + "</p>", payloadData, Map.of());

        AlertEvent recovery = new AlertEvent();
        recovery.setEventKey("SCHEDULE:" + keySuffix);
        recovery.setEventType(AlertEventType.SCHEDULE.name());
        recovery.setSeverity(AlertSeverity.P2.name());
        recovery.setScopeType("ALL");
        recovery.setScopeValue(null);
        recovery.setFirstSeen(now);
        recovery.setLastSeen(now);
        recovery.setFireCount(1);
        recovery.setStatus(AlertEventStatus.RESOLVED);
        recovery.setResolvedAt(now);
        recovery.setPayload(payload);
        eventRepo.save(recovery);
        AlertSilence silence = silenceService.findActiveFor("ALL", null, null, null).orElse(null);
        publishDeliveries(recovery, channels.stream().map(AlertChannel::getId).toList(), null, silence, null);
        return true;
    }

    @Transactional
    public AlertEvent publishTest(AlertTestEventRequest request, String username)
    {
        AlertEventType type = AlertEventType.SYSTEM;
        if (request != null && request.eventType() != null && !request.eventType().isBlank()) {
            try {
                type = AlertEventType.valueOf(request.eventType().trim().toUpperCase());
            }
            catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("不支持的事件类型: " + request.eventType());
            }
        }
        Instant now = Instant.now();
        String sourceDb = request == null || request.sourceDb() == null || request.sourceDb().isBlank()
            ? null : request.sourceDb().trim();
        String sourceTable = request == null || request.sourceTable() == null
            || request.sourceTable().isBlank() ? null : request.sourceTable().trim();
        Long tid = request == null ? null : request.tid();
        String scopeType = "ALL";
        String scopeValue = null;
        if (tid != null) {
            scopeType = "TABLE";
            scopeValue = String.valueOf(tid);
        }
        else if (sourceDb != null) {
            scopeType = "SOURCE";
            scopeValue = sourceDb;
        }
        String body = request == null || request.message() == null || request.message().isBlank()
            ? "这是一条由 " + (username == null ? "管理员" : username) + " 触发的测试告警，用于验证规则路由与投递链路。"
            : request.message().trim();
        String title = "测试告警 · " + type.name();
        String markdown = "## <font color=\"blue\"> Addax Admin 测试告警</font>\n"
            + "**时间**: " + TIME_FORMAT.format(now) + "\n"
            + "**主机**: " + messageFactory.hostname() + "\n"
            + "**事件类型**: " + type.name() + "\n"
            + (sourceDb == null ? "" : "**数据源**: " + sourceDb + "\n")
            + "---------------------------------\n"
            + "**内容**: " + body;
        String text = "【测试告警】" + type.name() + "：" + body;
        ObjectNode data = objectMapper.createObjectNode();
        data.put("test", true);
        data.put("eventType", type.name());
        data.put("message", body);
        data.put("triggeredBy", nullToEmpty(username));
        data.put("sourceDb", nullToEmpty(sourceDb));
        data.put("sourceTable", nullToEmpty(sourceTable));
        if (tid != null) {
            data.put("tid", tid);
        }
        JsonNode payload = messageFactory.payload(title, markdown, text,
            "<h2>" + AlertMessageFactory.escapeHtml(title) + "</h2><p>" + AlertMessageFactory.escapeHtml(body) + "</p>",
            data, Map.of());
        String eventKey = type.name() + ":TEST:" + UUID.randomUUID();
        // A test is a point-in-time fact, not an ongoing condition; leaving it FIRING
        // would clutter the event list with events that can never resolve.
        return recordEvent(eventKey, type, scopeType, scopeValue, sourceDb, payload, now,
            type.defaultSeverity(), true);
    }

    private boolean publishGlobal(AlertEventType type, String eventKey, String title,
        String message, String color, String category, AlertSeverity severity,
        Map<String, String> extraData, boolean resolvedFact)
    {
        lock(eventKey);
        Instant now = Instant.now();
        if (!resolvedFact) {
            Optional<AlertEvent> open = eventRepo.findFirstByEventKeyAndStatusInOrderByIdDesc(
                eventKey, List.of(AlertEventStatus.FIRING, AlertEventStatus.SILENCED));
            if (open.isPresent()) {
                AlertEvent event = open.get();
                event.setLastSeen(now);
                event.setFireCount(event.getFireCount() + 1);
                eventRepo.save(event);
                return false;
            }
        }
        String body = message == null ? "" : message;
        String markdown = "## <font color=\"" + color + "\"> " + title + "</font>\n"
            + "**时间**: " + TIME_FORMAT.format(now) + "\n"
            + "**主机**: " + messageFactory.hostname() + "\n"
            + "---------------------------------\n"
            + "**告警内容**: " + body;
        String text = "【" + title + "】" + body;
        ObjectNode data = objectMapper.createObjectNode();
        data.put("category", category);
        data.put("message", body);
        if (extraData != null) {
            extraData.forEach((key, value) -> data.put(key, nullToEmpty(value)));
        }
        JsonNode payload = messageFactory.payload(title, markdown, text,
            "<h2>" + AlertMessageFactory.escapeHtml(title) + "</h2><p>" + AlertMessageFactory.escapeHtml(body) + "</p>",
            data, Map.of());
        recordEvent(eventKey, type, "ALL", null, null, payload, now, severity, resolvedFact);
        return true;
    }

    private AlertEvent recordEvent(String eventKey, AlertEventType type, String scopeType, String scopeValue,
        String sourceDb, JsonNode payload, Instant now, AlertSeverity severity, boolean resolvedFact)
    {
        AlertEvent event = new AlertEvent();
        event.setEventKey(eventKey);
        event.setEventType(type.name());
        event.setSeverity(severity.name());
        event.setScopeType(scopeType);
        event.setScopeValue(scopeValue);
        event.setFirstSeen(now);
        event.setLastSeen(now);
        event.setFireCount(1);
        event.setPayload(payload);
        event.setStatus(AlertEventStatus.FIRING);

        AlertRouter.RouteResult route = router.route(event);
        event.setSeverity(route.severity().name());
        JsonNode data = payload.path("data");
        String sourceTable = data.path("sourceTable").isMissingNode() ? null : data.path("sourceTable").asText(null);
        Optional<AlertSilence> silence = silenceService.findActiveFor(scopeType, scopeValue, sourceDb,
            sourceTable);
        if (silence.isPresent()) {
            event.setStatus(AlertEventStatus.SILENCED);
        }
        // A silenced fact keeps SILENCED: the muting decision is the fact an
        // operator needs to see, and resolving it would hide that it was muted.
        if (resolvedFact && silence.isEmpty()) {
            event.setStatus(AlertEventStatus.RESOLVED);
            event.setResolvedAt(now);
        }
        eventRepo.save(event);
        // Informational types are opt-in: the default "no rule means every channel"
        // route must not turn a once-a-day summary into a daily broadcast.
        boolean informationalUnsubscribed = type == AlertEventType.DAILY && !route.matchedRule();
        if (informationalUnsubscribed) {
            publishDeliveries(event, List.of(), sourceDb, silence.orElse(null),
                "信息类事件未订阅：请为「每日汇总」建立启用规则后再外发");
            return event;
        }
        publishDeliveries(event, route.channelIds(), sourceDb, silence.orElse(null), null);
        return event;
    }

    private void publishDeliveries(AlertEvent event, List<Long> channelIds, String sourceDb,
        AlertSilence silence, String skipReason)
    {
        Map<Long, AlertChannel> channels = new LinkedHashMap<>();
        if (channelIds != null) {
            channelRepo.findAllById(channelIds).forEach(channel -> channels.put(channel.getId(), channel));
        }
        if (channels.isEmpty()) {
            AlertDelivery skipped = baseDelivery(event, null, null, AlertDeliveryStatus.SKIPPED_NO_CHANNEL);
            if (silence != null) {
                skipped.setResponseBody("告警已静默：" + nullToEmpty(silence.getReason()));
            }
            else if (skipReason != null) {
                skipped.setResponseBody(skipReason);
            }
            else {
                skipped.setResponseBody("告警已暂停：没有已启用且被规则选中的通道");
            }
            deliveryRepo.save(skipped);
            log.warn("Alert event {} has no deliverable channel; recorded as SKIPPED_NO_CHANNEL", event.getEventKey());
            return;
        }
        for (AlertChannel channel : channels.values()) {
            if (silence != null) {
                AlertDelivery delivery = baseDelivery(event, channel.getId(), channel.getName(),
                    AlertDeliveryStatus.SILENCED);
                delivery.setResponseBody("告警已静默：" + nullToEmpty(silence.getReason()));
                deliveryRepo.save(delivery);
            }
            else {
                AlertDelivery delivery = baseDelivery(event, channel.getId(), channel.getName(),
                    AlertDeliveryStatus.PENDING);
                delivery.setNextRetryAt(Instant.now());
                deliveryRepo.save(delivery);
            }
        }
    }

    private AlertDelivery baseDelivery(AlertEvent event, Long channelId, String channelName,
        AlertDeliveryStatus status)
    {
        AlertDelivery delivery = new AlertDelivery();
        delivery.setEventId(event.getId());
        delivery.setChannelId(channelId);
        delivery.setChannelName(channelName);
        delivery.setStatus(status);
        delivery.setAttempts(0);
        return delivery;
    }

    /**
     * Serializes dispatchers for the same event key across nodes. Without it two
     * concurrent failures could both observe "no open event" and create duplicate
     * deliveries; the partial unique index would then reject one transaction
     * after its deliveries were already written.
     */
    private void lock(String eventKey)
    {
        int key = eventKey.hashCode();
        jdbcTemplate.query("select pg_advisory_xact_lock(?)", rs -> {
        }, (long) key);
    }

    private String taskDescription(long tid, String sourceDb, String sourceTable)
    {
        if (sourceDb == null || sourceDb.isBlank()) {
            return "任务(" + tid + ")";
        }
        return sourceDb + (sourceTable == null || sourceTable.isBlank() ? "" : "." + sourceTable) + "(" + tid + ")";
    }

    private static String shortHash(String value)
    {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(nullToEmpty(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        }
        catch (Exception ex) {
            return Integer.toHexString(value == null ? 0 : value.hashCode());
        }
    }

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max)
    {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
