package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.SchemaChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemaChangeLogRepo
    extends JpaRepository<SchemaChangeLog, Long>
{
}
