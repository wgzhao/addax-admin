package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.SystemFlag;
import com.wgzhao.addax.admin.repository.SystemFlagRepo;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@AllArgsConstructor
public class SystemFlagService {
    public static final String KEY_SCHEMA_REFRESH = "schema_refresh_in_progress";

    private final SystemFlagRepo systemFlagRepo;

    @PostConstruct
    public void ensureFlagRow() {
        Optional<SystemFlag> maybe = systemFlagRepo.findById(KEY_SCHEMA_REFRESH);
        if (maybe.isEmpty()) {
            SystemFlag f = new SystemFlag();
            f.setFlagKey(KEY_SCHEMA_REFRESH);
            f.setFlagValue("0");
            f.setUpdatedAt(LocalDateTime.now());
            f.setUpdatedBy("system");
            systemFlagRepo.save(f);
        }
    }

    /**
     * 尝试占用刷新标志，返回 true 表示成功。使用乐观更新：当当前 value!= "1" 时设置为 "1"。
     */
    @Transactional
    public boolean beginRefresh(String who) {
        LocalDateTime now = LocalDateTime.now();
        // try to set value to '1' only if current value is not '1'
        int updated = systemFlagRepo.trySetValueIfNotEqual(KEY_SCHEMA_REFRESH, "1", now, who);
        return updated > 0;
    }

    @Transactional
    public void endRefresh(String who) {
        LocalDateTime now = LocalDateTime.now();
        systemFlagRepo.setValueAndFinish(KEY_SCHEMA_REFRESH, "0", now, who);
    }

    public boolean isRefreshInProgress() {
        return systemFlagRepo.findById(KEY_SCHEMA_REFRESH)
                .map(f -> "1".equals(f.getFlagValue()))
                .orElse(false);
    }
}
