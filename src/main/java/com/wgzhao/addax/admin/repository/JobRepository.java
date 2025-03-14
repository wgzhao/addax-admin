package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.CollectJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<CollectJob, Long> {
    
    /**
     * 查找需要执行的作业
     * @param currentTime 当前时间
     * @return 需要执行的作业列表
     */
    @Query("SELECT j FROM CollectJob j WHERE j.nextFireTime <= :currentTime AND j.jobStatus IN ('N', 'P') ORDER BY j.nextFireTime")
    List<CollectJob> findJobsToExecute(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * 根据作业名称查找作业
     * @param jobName 作业名称
     * @return 作业对象
     */
    CollectJob findByJobName(String jobName);
    
    /**
     * 查找指定分组的作业
     * @param jobGroup 作业分组
     * @return 作业列表
     */
    List<CollectJob> findByJobGroup(String jobGroup);
}