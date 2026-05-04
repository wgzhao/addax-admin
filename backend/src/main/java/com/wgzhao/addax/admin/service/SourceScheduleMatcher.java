package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.CollectDateMode;
import com.wgzhao.addax.admin.model.EtlSource;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Component
public class SourceScheduleMatcher
{
    public boolean matches(EtlSource source, LocalDate bizDate)
    {
        if (source == null || bizDate == null) {
            return false;
        }
        return matches(resolveMode(source), bizDate.getDayOfWeek());
    }

    public List<Integer> resolveMatchedEnabledSourceIds(List<EtlSource> enabledSources, LocalDate bizDate)
    {
        if (enabledSources == null || enabledSources.isEmpty() || bizDate == null) {
            return List.of();
        }
        DayOfWeek dayOfWeek = bizDate.getDayOfWeek();
        return enabledSources.stream()
            .filter(EtlSource::isEnabled)
            .filter(source -> matches(resolveMode(source), dayOfWeek))
            .map(EtlSource::getId)
            .toList();
    }

    private boolean matches(CollectDateMode mode, DayOfWeek dayOfWeek)
    {
        if (mode == CollectDateMode.WEEKDAY) {
            return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
        }
        if (mode == CollectDateMode.WEEKEND) {
            return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        }
        return true;
    }

    private CollectDateMode resolveMode(EtlSource source)
    {
        return source.getCollectDateMode() == null ? CollectDateMode.DAILY : source.getCollectDateMode();
    }
}

