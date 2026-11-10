package com.wgzhao.addax.admin.alert;

import com.wgzhao.addax.admin.alert.dto.AlertSilenceDto;
import com.wgzhao.addax.admin.alert.dto.AlertSilenceRequest;
import com.wgzhao.addax.admin.alert.model.AlertSilence;
import com.wgzhao.addax.admin.alert.repo.AlertSilenceRepo;
import com.wgzhao.addax.admin.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AlertSilenceService
{
    private static final Set<String> SCOPE_TYPES = Set.of("ALL", "SOURCE", "TABLE");

    private final AlertSilenceRepo silenceRepo;

    public List<AlertSilenceDto> list()
    {
        Instant now = Instant.now();
        return silenceRepo.findAllByOrderByEndsAtDesc().stream()
            .map(silence -> toDto(silence, now)).toList();
    }

    @Transactional
    public AlertSilenceDto create(AlertSilenceRequest request, String username)
    {
        String scopeType = request.scopeType() == null ? "ALL" : request.scopeType().trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_TYPES.contains(scopeType)) {
            throw new ApiException(400, "静默范围只支持 ALL/SOURCE/TABLE");
        }
        String scopeValue = request.scopeValue() == null ? null : request.scopeValue().trim();
        if ("ALL".equals(scopeType)) {
            scopeValue = null;
        }
        else if (scopeValue == null || scopeValue.isEmpty()) {
            throw new ApiException(400, "范围不是 ALL 时必须填写范围值");
        }
        if (request.startsAt() == null || request.endsAt() == null) {
            throw new ApiException(400, "静默开始和结束时间不能为空");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(400, "静默结束时间必须晚于开始时间");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() > 255) {
            throw new ApiException(400, "静默原因不能超过 255 个字符");
        }
        AlertSilence silence = new AlertSilence();
        silence.setScopeType(scopeType);
        silence.setScopeValue(scopeValue);
        silence.setStartsAt(request.startsAt());
        silence.setEndsAt(request.endsAt());
        silence.setReason(reason.isEmpty() ? null : reason);
        silence.setCreatedBy(username);
        silenceRepo.save(silence);
        return toDto(silence, Instant.now());
    }

    @Transactional
    public void delete(long id)
    {
        AlertSilence silence = silenceRepo.findById(id)
            .orElseThrow(() -> new ApiException(404, "静默记录不存在: " + id));
        silenceRepo.delete(silence);
    }

    /**
     * Returns the first active silence matching an event. SOURCE silences compare
     * with the source database name; TABLE silences accept the collection table id,
     * the source table name, or the sourceDb.sourceTable pair.
     */
    public Optional<AlertSilence> findActiveFor(String eventScopeType, String eventScopeValue,
        String sourceDb, String sourceTable)
    {
        Instant now = Instant.now();
        for (AlertSilence silence : silenceRepo.findActive(now)) {
            String type = silence.getScopeType();
            String value = silence.getScopeValue();
            if ("ALL".equals(type)) {
                return Optional.of(silence);
            }
            if ("SOURCE".equals(type) && sourceDb != null && sourceDb.equalsIgnoreCase(value)) {
                return Optional.of(silence);
            }
            if ("TABLE".equals(type) && value != null) {
                if (value.equals(eventScopeValue) || value.equalsIgnoreCase(eventScopeValue)) {
                    return Optional.of(silence);
                }
                if (sourceTable != null && value.equalsIgnoreCase(sourceTable)) {
                    return Optional.of(silence);
                }
                if (sourceDb != null && sourceTable != null
                    && value.equalsIgnoreCase(sourceDb + "." + sourceTable)) {
                    return Optional.of(silence);
                }
            }
        }
        return Optional.empty();
    }

    private AlertSilenceDto toDto(AlertSilence silence, Instant now)
    {
        boolean active = !silence.getStartsAt().isAfter(now) && silence.getEndsAt().isAfter(now);
        return new AlertSilenceDto(silence.getId(), silence.getScopeType(), silence.getScopeValue(),
            silence.getStartsAt(), silence.getEndsAt(), silence.getReason(), silence.getCreatedBy(),
            silence.getCreatedAt(), active);
    }
}
