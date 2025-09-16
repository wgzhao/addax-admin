package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.model.AddaxLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface AddaxLogRepo extends JpaRepository<AddaxLog, Long>
{
    AddaxLog findFirstByTidOrderByRunDateDesc(String tid);

    List<AddaxLog> findTop5ByTidOrderByRunDateDesc(String tid);

    List<AddaxLog> findTop5ByTidAndRunDateGreaterThanOrderByIdDesc(String tid, LocalDate runDate);

    AddaxLog findByTidAndRunDate(String tid, LocalDate runDate);

    @Query(value = """
            select new com.wgzhao.addax.admin.dto.AddaxLogDto(a.id, to_char(a.runAt, 'YYYY-MM-DD HH24:MI:SS'))
            from AddaxLog a
            where a.tid = ?1
            order by a.runAt desc
            limit 5
            """)
    List<AddaxLogDto> findLogEntry(String tid);
}
