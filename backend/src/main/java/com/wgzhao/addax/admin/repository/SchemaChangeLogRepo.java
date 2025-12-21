package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.SchemaChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SchemaChangeLogRepo
    extends JpaRepository<SchemaChangeLog, Long>
{
    List<SchemaChangeLog> findByTidAndChangeAtBetweenOrderByChangeAtDesc(long tid, LocalDateTime start, LocalDateTime end);
}
