package com.wgzhao.fsbrowser.repository.pg;

import com.wgzhao.fsbrowser.model.pg.VwAddaxLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VwAddaxLogRepo extends JpaRepository<VwAddaxLog, String> {
    List<VwAddaxLog> findTop15BySpnameIn(List<String> spNames);
}
