package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.TableEvent;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@AllArgsConstructor
public class TableStateService {

    private final EtlTableRepo etlTableRepo;
    private final EtlJourService etlJourService;

    // 定义状态流转规则
    private static final Map<TableStatus, Set<TableStatus>> transitions = Map.of(
            TableStatus.INITIAL, Set.of(TableStatus.REFRESHING, TableStatus.DISABLED),
            TableStatus.REFRESHING, Set.of(TableStatus.READY, TableStatus.FAILED, TableStatus.DISABLED, TableStatus.SCHEMA_CHANGED),
            TableStatus.READY, Set.of(TableStatus.COLLECTING, TableStatus.DISABLED, TableStatus.SCHEMA_CHANGED, TableStatus.REFRESHING),
            TableStatus.COLLECTING, Set.of(TableStatus.COLLECTED, TableStatus.FAILED, TableStatus.DISABLED),
            TableStatus.COLLECTED, Set.of(TableStatus.READY, TableStatus.DISABLED, TableStatus.SCHEMA_CHANGED, TableStatus.REFRESHING),
            TableStatus.FAILED, Set.of(TableStatus.READY, TableStatus.REFRESHING, TableStatus.DISABLED),
            TableStatus.DISABLED, Set.of(TableStatus.READY, TableStatus.INITIAL),
            TableStatus.SCHEMA_CHANGED, Set.of(TableStatus.REFRESHING, TableStatus.DISABLED, TableStatus.READY)
    );

    @Transactional
    public void transition(Long tableId, TableEvent event) {
        EtlTable table = etlTableRepo.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with id: " + tableId));

        // 兜底：当数据库中 status 为 null 或不可识别时，视为 INITIAL
        TableStatus currentStatus;
        try {
            currentStatus = TableStatus.fromCode(table.getStatus() == null ? TableStatus.INITIAL.getCode() : table.getStatus());
        } catch (Exception e) {
            log.warn("Unknown status '{}' for table {}, defaulting to INITIAL", table.getStatus(), tableId);
            currentStatus = TableStatus.INITIAL;
        }
        TableStatus nextStatus = getNextStatus(currentStatus, event);

        // 允许自循环（无状态变化）作为合法操作，避免不必要的异常
        if (nextStatus == currentStatus) {
            log.info("Table {} no-op transition on event {} (status stays {})", tableId, event, currentStatus);
            return;
        }

        if (isValidTransition(currentStatus, nextStatus)) {
            table.setStatus(nextStatus.getCode());
            etlTableRepo.save(table);
            log.info("Table {} transitioned from {} to {} on event {}", tableId, currentStatus, nextStatus, event);
            // 记录状态流转到 etl_jour
            String message = String.format("Event: %s, Status changed from %s to %s",
                    event.name(), currentStatus.name(), nextStatus.name());
            etlJourService.addJour(tableId, JourKind.STATE_CHANGE, message);
        } else {
            log.warn("Invalid state transition from {} to {} for table {}", currentStatus, nextStatus, tableId);
            throw new IllegalStateException("Invalid state transition from " + currentStatus + " to " + nextStatus);
        }
    }

    private boolean isValidTransition(TableStatus from, TableStatus to) {
        if (from == to) {
            return true;
        }
        return transitions.getOrDefault(from, Set.of()).contains(to);
    }

    private TableStatus getNextStatus(TableStatus currentStatus, TableEvent event) {
        return switch (event) {
            case START_PROCESSING -> TableStatus.REFRESHING;
            case PROCESS_SUCCESS -> TableStatus.READY;
            case PROCESS_FAIL -> TableStatus.FAILED;
            case START_COLLECT -> TableStatus.COLLECTING;
            case COLLECT_SUCCESS -> TableStatus.COLLECTED;
            case COLLECT_FAIL -> TableStatus.FAILED;
            case DISABLE -> TableStatus.DISABLED;
            case ENABLE -> currentStatus == TableStatus.DISABLED ? TableStatus.INITIAL : currentStatus;
            case DETECT_SCHEMA_CHANGE -> TableStatus.SCHEMA_CHANGED;
            case SCHEMA_CHANGE_PROCESSED -> TableStatus.READY;
            case RETRY -> {
                if (currentStatus == TableStatus.FAILED) {
                    yield TableStatus.READY;
                }
                yield currentStatus;
            }
        };
    }
}
