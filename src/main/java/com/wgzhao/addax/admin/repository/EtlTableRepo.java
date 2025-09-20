package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlTable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalTime;
import java.util.List;

public interface EtlTableRepo
        extends JpaRepository<EtlTable, Long>
{

    @Query(value = """
            SELECT t.sourceTable FROM EtlTable t WHERE t.etlSource.id = :sysId AND t.sourceDb = :souOwner
            """)
    List<String> findTables(int sysId, String souOwner);

    @Query(value = """
            select t.id from EtlTable t
            join t.etlSource d
            where t.status <> 'X' and t.updateFlag = 'N' and t.createFlag = 'N'
                  and d.enabled = true
            """)
    List<Long> findValidTids();

    List<EtlTable> findByStatus(String etlFlag, Sort by);

    Page<EtlTable> findAllProjectedBy(Pageable page);

    List<EtlTable> findByCreateFlagOrUpdateFlag(String createFlag, String updateFlag);

    @Modifying
    @Query("UPDATE EtlTable t SET t.status = 'N', t.retryCnt = 3 where t.status <> 'X'")
    void resetAllEtlFlags();

    @Query(value = """
            select t
            from EtlTable t
            where (t.status='E' or t.duration>=1200 or t.retryCnt<3) and t.status <> 'X'
            order by t.duration desc
            """)
    List<EtlTable> findSpecialTasks();

    @Modifying
    @Query(value = """
                    update EtlTable t
                    set t.status = ?2, t.retryCnt = ?3
                    where t.id in ?1
            """)
    void batchUpdateStatusAndFlag(List<String> ids, String status, int retryCnt);

    int countByStatusEquals(String n);

    @Query("""
        SELECT t FROM EtlTable t JOIN t.etlSource d
        WHERE t.status = 'N' AND t.retryCnt > 0 AND t.updateFlag = 'N' AND t.createFlag = 'N'
        AND (
            :checkTime = false OR
            (d.startAt > :switchTime AND d.startAt < :currentTime)
        )
    """)
    List<EtlTable> findRunnableTasks(LocalTime switchTime, LocalTime currentTime, boolean checkTime);

    @Query("SELECT t FROM EtlTable t JOIN t.etlSource d WHERE t.status <> 'X' AND d.enabled = true")
    List<EtlTable> findValidTables();

    Page<EtlTable> findByFilterColumnContaining(String upperCase, Pageable pageable);

    Page<EtlTable> findByStatusAndFilterColumnContaining(String status, String upperCase, Pageable pageable);
}
