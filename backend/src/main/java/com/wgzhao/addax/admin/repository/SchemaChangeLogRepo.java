package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.SchemaChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SchemaChangeLogRepo
    extends JpaRepository<SchemaChangeLog, Long>, JpaSpecificationExecutor<SchemaChangeLog>
{
}
