package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.model.UserNotification;
import com.wgzhao.addax.admin.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user-notifications")
@RequiredArgsConstructor
public class UserNotificationController
{
    private final UserNotificationService notificationService;

    @GetMapping("")
    public ResponseEntity<List<UserNotification>> list(
        @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
        @RequestParam(value = "limit", required = false, defaultValue = "20") int limit)
    {
        String username = getCurrentUsername();
        return ResponseEntity.ok(notificationService.list(username, status, limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount()
    {
        String username = getCurrentUsername();
        long count = notificationService.unreadCount(username);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable("id") long id)
    {
        String username = getCurrentUsername();
        boolean ok = notificationService.markRead(username, id);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead()
    {
        String username = getCurrentUsername();
        int count = notificationService.markAllRead(username);
        return ResponseEntity.ok(Map.of("success", true, "count", count));
    }

    private String getCurrentUsername()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getName();
    }
}
