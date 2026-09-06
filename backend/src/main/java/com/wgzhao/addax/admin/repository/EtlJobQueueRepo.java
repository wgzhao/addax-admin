package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlJobQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EtlJobQueueRepo
    extends JpaRepository<EtlJobQueue, Long>
{
    long countByStatus(String pending);
}

