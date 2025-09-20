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
            SELECT t.souTablename FROM EtlTable t WHERE t.souSysid = :sysId AND t.souOwner = :souOwner
            """)
    List<String> findTables(String sysId, String souOwner);

    @Query(value = """
            select t.tid from EtlTable t where t.status <> 'X' and t.updateFlag = 'N' and t.createFlag = 'N'
            """)
    List<Long> findValidTids();

    List<EtlTable> findByFlag(String etlFlag, Sort by);

    Page<EtlTable> findAllProjectedBy(Pageable page);

    @Query("SELECT t FROM EtlTable t WHERE t.bupdate = 'Y' OR t.bcreate = 'Y'")
    List<EtlTable> findByBupdateOrBcreateIsY();

    @Modifying
    @Query("UPDATE EtlTable t SET t.flag = 'N' where t.flag < 'X'")
    void resetAllEtlFlags();

    @Query(value = """
            select t
            from EtlTable t
            where (t.flag='E' or t.runtime>=1200 or t.retryCnt<3) and t.flag <> 'X'
            order by t.flag asc,t. runtime desc
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
}
