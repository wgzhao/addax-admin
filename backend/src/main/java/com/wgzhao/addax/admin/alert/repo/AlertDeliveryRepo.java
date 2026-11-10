package com.wgzhao.addax.admin.alert.repo;

import com.wgzhao.addax.admin.alert.AlertDeliveryStatus;
import com.wgzhao.addax.admin.alert.model.AlertDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertDeliveryRepo extends JpaRepository<AlertDelivery, Long>, JpaSpecificationExecutor<AlertDelivery>
{
    @Query("""
        select distinct d.channelId from AlertDelivery d
         where d.eventId = :eventId
           and d.channelId is not null
           and d.status in :statuses
        """)
    List<Long> findChannelIdsByEventId(@Param("eventId") Long eventId,
        @Param("statuses") Collection<AlertDeliveryStatus> statuses);

    Optional<AlertDelivery> findFirstByEventIdAndChannelId(Long eventId, Long channelId);
}
