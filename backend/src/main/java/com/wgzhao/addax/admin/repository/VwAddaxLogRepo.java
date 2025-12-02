package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.VwAddaxLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VwAddaxLogRepo extends JpaRepository<VwAddaxLog, String> {
    List<VwAddaxLog> findTop15BySpnameIn(List<String> spNames);
}
