package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.EtlTableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;

public interface EtlTableRepo extends JpaRepository<EtlTable, Long> {

    // 获取所有有效表数量（非禁用）
    @Query("select count(t.id) from EtlTable t where t.status <> 99")
    Integer findValidTableCount();

    // 重置所有表为待采集状态，重试次数重置为3（非禁用）
    @Modifying
    @Query("UPDATE EtlTable t SET t.status = 40, t.retryCnt = 3 where t.status <> 99")
    void resetAllEtlFlags();

    // 查找特殊任务（失败、超时、重试次数不足，且非禁用）
    @Query("select t from EtlTable t where (t.status = 30 or t.duration >= 1200 or t.retryCnt < 3) and t.status <> 99 order by t.duration desc")
    List<EtlTable> findSpecialTasks();

    // 批量更新状态和重试次数
    @Modifying
    @Query("update EtlTable t set t.status = :#{#status.code}, t.retryCnt = :retryCnt where t.id in :ids")
    void batchUpdateStatusAndFlag(@Param("ids") List<Long> ids, @Param("status") EtlTableStatus status, @Param("retryCnt") int retryCnt);

    // 统计指定状态的表数量
    int countByStatus(EtlTableStatus status);

    // 查找可运行任务（非禁用、重试次数大于0、状态为等待执行/采集/更新/建表等）
    @Query("""
        SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
        WHERE t.status IN :statusList AND t.retryCnt > 0 AND t.status <> :disabledStatus
        AND (
            :checkTime = false OR
            (s.startAt > :switchTime AND s.startAt < :currentTime)
        )
    """)
    List<EtlTable> findRunnableTasks(
        @Param("statusList") List<EtlTableStatus> statusList,
        @Param("switchTime") LocalTime switchTime,
        @Param("currentTime") LocalTime currentTime,
        @Param("checkTime") boolean checkTime,
        @Param("disabledStatus") EtlTableStatus disabledStatus
    );

    // 获取所有有效表（非禁用）
    @Query("SELECT t FROM EtlTable t JOIN EtlSource s WHERE t.status <> 99 AND s.enabled = true")
    List<EtlTable> findValidTables();

    int countByStatusEquals(EtlTableStatus etlTableStatus);
}
