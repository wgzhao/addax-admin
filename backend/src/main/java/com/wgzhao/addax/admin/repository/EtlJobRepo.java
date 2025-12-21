package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlJobRepo
    extends JpaRepository<EtlJob, Long>
{
}
