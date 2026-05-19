package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlTableChangeLog;
import com.wgzhao.addax.admin.repository.EtlTableChangeLogRepo;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class EtlTableChangeLogService
{
    private final EtlTableChangeLogRepo repo;

    public Page<EtlTableChangeLog> getTableChanges(long tableId, int page, int size, String field)
    {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        if (field == null || field.isBlank()) {
            return repo.findByTidOrderByChangedAtDescIdDesc(tableId, pageable);
        }
        return repo.findByTidAndChangedField(tableId, field.trim(), pageable);
    }
}
