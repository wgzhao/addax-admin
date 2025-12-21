package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;

public interface EtlTableRepo
    extends JpaRepository<EtlTable, Long>
{
    @Query(value = """
        select count(t.id) from VwEtlTableWithSource t
        where t.status not in ( 'X' ,'U')
              and t.enabled = true
        """)
    Integer findValidTableCount();

    @Modifying
    @Query("UPDATE EtlTable t SET t.status = 'N', t.retryCnt = 3 where t.status not in ( 'X', 'U')")
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
    void batchUpdateStatusAndFlag(List<Long> ids, String status, int retryCnt);

    int countByStatusEquals(String n);

    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
            WHERE t.status NOT IN ('Y','X','U') AND t.retryCnt > 0
            AND (
                :checkTime = false OR
                (s.startAt > :switchTime AND s.startAt < :currentTime)
            )
        """)
    List<EtlTable> findRunnableTasks(@Param("switchTime") LocalTime switchTime,
        @Param("currentTime") LocalTime currentTime,
        @Param("checkTime") boolean checkTime);

    @Query("SELECT t FROM EtlTable t JOIN EtlSource s WHERE t.status <> 'X' AND s.enabled = true")
    List<EtlTable> findValidTables();

    int countBySid(int sid);

    List<EtlTable> findByStatus(String status);

    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
                WHERE t.status <> 'X' AND s.enabled = true
        """)
    List<EtlTable> findCanRefreshTables();
}
