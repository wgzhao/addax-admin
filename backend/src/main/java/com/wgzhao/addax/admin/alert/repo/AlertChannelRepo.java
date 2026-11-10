package com.wgzhao.addax.admin.alert.repo;

import com.wgzhao.addax.admin.alert.model.AlertChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertChannelRepo extends JpaRepository<AlertChannel, Long>
{
    List<AlertChannel> findAllByOrderByIdAsc();

    List<AlertChannel> findByEnabledTrueOrderByIdAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByEnabledTrue();
}
