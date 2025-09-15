package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.AddaxLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AddaxLogRepo extends JpaRepository<AddaxLog, Long>
{
    AddaxLog findFirstByTidOrderByRunDateDesc(String tid);

    List<AddaxLog> findTop5ByTidOrderByRunDateDesc(String tid);

    List<AddaxLog> findTop5ByTidAndRunDateGreaterThanOrderByIdDesc(String tid, LocalDate runDate);
}
