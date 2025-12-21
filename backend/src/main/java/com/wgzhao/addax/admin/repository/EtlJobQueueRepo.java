package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlJobQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EtlJobQueueRepo
    extends JpaRepository<EtlJobQueue, Long>
{

    @Query("select count(e) from EtlJobQueue e where e.status in ('pending','running')")
    long countActive();

    @Query("select count(e) from EtlJobQueue e where e.status = 'pending'")
    long countPending();

    @Query("select count(e) from EtlJobQueue e where e.status = 'running'")
    long countRunning();

    @Query("select count(e) from EtlJobQueue e where e.status = 'failed'")
    long countFailed();

    @Query("select count(e) from EtlJobQueue e where e.status = 'completed'")
    long countCompleted();

    void deleteByStatusNot(String status);
}

