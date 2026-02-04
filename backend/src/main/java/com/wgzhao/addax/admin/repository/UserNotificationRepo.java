package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.UserNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserNotificationRepo extends JpaRepository<UserNotification, Long>
{
    long countByUsernameAndStatus(String username, String status);

    List<UserNotification> findByUsernameAndStatusOrderByCreatedAtDesc(String username, String status, Pageable pageable);

    List<UserNotification> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    Optional<UserNotification> findByIdAndUsername(Long id, String username);

    @Modifying
    @Query("UPDATE UserNotification n SET n.status = 'READ', n.readAt = CURRENT_TIMESTAMP WHERE n.username = :username AND n.status = 'UNREAD'")
    int markAllRead(@Param("username") String username);
}
