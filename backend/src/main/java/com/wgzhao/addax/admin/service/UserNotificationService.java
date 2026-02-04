package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.UserNotification;
import com.wgzhao.addax.admin.repository.UserNotificationRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserNotificationService
{
    private static final String STATUS_UNREAD = "UNREAD";
    private static final String STATUS_READ = "READ";

    private final UserNotificationRepo notificationRepo;

    public void create(String username, String title, String content, String type, String refType, String refId)
    {
        if (username == null || username.isBlank()) {
            return;
        }
        UserNotification notification = new UserNotification();
        notification.setUsername(username);
        notification.setTitle(title == null ? "" : title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRefType(refType);
        notification.setRefId(refId);
        notification.setStatus(STATUS_UNREAD);
        notificationRepo.save(notification);
    }

    public long unreadCount(String username)
    {
        if (username == null || username.isBlank()) {
            return 0L;
        }
        return notificationRepo.countByUsernameAndStatus(username, STATUS_UNREAD);
    }

    public List<UserNotification> list(String username, String status, int limit)
    {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return notificationRepo.findByUsernameOrderByCreatedAtDesc(username, PageRequest.of(0, safeLimit));
        }
        return notificationRepo.findByUsernameAndStatusOrderByCreatedAtDesc(username, status.toUpperCase(), PageRequest.of(0, safeLimit));
    }

    @Transactional
    public boolean markRead(String username, long id)
    {
        return notificationRepo.findByIdAndUsername(id, username)
            .map(n -> {
                if (!STATUS_READ.equalsIgnoreCase(n.getStatus())) {
                    n.setStatus(STATUS_READ);
                    n.setReadAt(Instant.now());
                    notificationRepo.save(n);
                }
                return true;
            })
            .orElse(false);
    }

    @Transactional
    public int markAllRead(String username)
    {
        if (username == null || username.isBlank()) {
            return 0;
        }
        return notificationRepo.markAllRead(username);
    }
}
