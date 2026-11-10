package com.wgzhao.addax.admin.alert.repo;

import com.wgzhao.addax.admin.alert.AlertEventStatus;
import com.wgzhao.addax.admin.alert.model.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;

public interface AlertEventRepo extends JpaRepository<AlertEvent, Long>, JpaSpecificationExecutor<AlertEvent>
{
    Optional<AlertEvent> findFirstByEventKeyAndStatusInOrderByIdDesc(
        String eventKey, Collection<AlertEventStatus> statuses);
}
