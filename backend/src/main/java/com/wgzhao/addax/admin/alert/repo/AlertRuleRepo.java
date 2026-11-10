package com.wgzhao.addax.admin.alert.repo;

import com.wgzhao.addax.admin.alert.model.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepo extends JpaRepository<AlertRule, Long>
{
    List<AlertRule> findAllByOrderByIdAsc();

    List<AlertRule> findByEnabledTrueOrderByIdAsc();
}
