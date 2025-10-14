package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.EtlTable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalTime

interface EtlTableRepo

    : JpaRepository<EtlTable?, Long?> {
    @Query(
        value = """
            select count(t.id) from VwEtlTableWithSource t
            where t.status not in ( 'X' ,'U')
                  and t.enabled = true
            
            """
    )
    fun findValidTableCount(): Int?

    @Modifying
    @Query("UPDATE EtlTable t SET t.status = 'N', t.retryCnt = 3 where t.status not in ( 'X', 'U')")
    fun resetAllEtlFlags()

    @Query(
        value = """
            select t
            from EtlTable t
            where (t.status='E' or t.duration>=1200 or t.retryCnt<3) and t.status <> 'X'
            order by t.duration desc
            
            """
    )
    fun findSpecialTasks(): MutableList<EtlTable?>?

    @Modifying
    @Query(
        value = """
                    update EtlTable t
                    set t.status = ?2, t.retryCnt = ?3
                    where t.id in ?1
            
            """
    )
    fun batchUpdateStatusAndFlag(ids: MutableList<Long?>?, status: String?, retryCnt: Int)

    fun countByStatusEquals(n: String?): Int

    @Query(
        """
        SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
        WHERE t.status NOT IN ('Y','X','U') AND t.retryCnt > 0
        AND (
            :checkTime = false OR
            (s.startAt > :switchTime AND s.startAt < :currentTime)
        )
    
    """
    )
    fun findRunnableTasks(switchTime: LocalTime?, currentTime: LocalTime?, checkTime: Boolean): MutableList<EtlTable?>?

    @Query("SELECT t FROM EtlTable t JOIN EtlSource s WHERE t.status <> 'X' AND s.enabled = true")
    fun findValidTables(): MutableList<EtlTable?>?

    fun countBySid(sid: Int): Int
}
