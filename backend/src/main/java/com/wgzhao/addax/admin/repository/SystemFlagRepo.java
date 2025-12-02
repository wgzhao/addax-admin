package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.SystemFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface SystemFlagRepo extends JpaRepository<SystemFlag, String> {

    @Modifying
    @Transactional
    @Query("UPDATE SystemFlag f SET f.flagValue = :newValue, f.lastStartedAt = :startedAt, f.updatedAt = :startedAt, f.updatedBy = :updatedBy WHERE f.flagKey = :key AND (f.flagValue IS NULL OR f.flagValue <> :newValue)")
    int trySetValueIfNotEqual(@Param("key") String key, @Param("newValue") String newValue, @Param("startedAt") LocalDateTime startedAt, @Param("updatedBy") String updatedBy);

    @Modifying
    @Transactional
    @Query("UPDATE SystemFlag f SET f.flagValue = :newValue, f.lastFinishedAt = :finishedAt, f.updatedAt = :finishedAt, f.updatedBy = :updatedBy WHERE f.flagKey = :key")
    int setValueAndFinish(@Param("key") String key, @Param("newValue") String newValue, @Param("finishedAt") LocalDateTime finishedAt, @Param("updatedBy") String updatedBy);
}
