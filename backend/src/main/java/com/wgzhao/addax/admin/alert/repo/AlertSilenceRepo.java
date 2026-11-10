package com.wgzhao.addax.admin.alert.repo;

import com.wgzhao.addax.admin.alert.model.AlertSilence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AlertSilenceRepo extends JpaRepository<AlertSilence, Long>
{
    List<AlertSilence> findAllByOrderByEndsAtDesc();

    @Query("select s from AlertSilence s where s.startsAt <= :now and s.endsAt > :now")
    List<AlertSilence> findActive(@Param("now") Instant now);
}
