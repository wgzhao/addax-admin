package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.CreateUserDTO;
import com.wgzhao.addax.admin.dto.UpdateUserDTO;
import com.wgzhao.addax.admin.dto.UserAdminDto;
import com.wgzhao.addax.admin.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController
{
    private final UserAdminService userAdminService;
    private final AuditRecorder auditRecorder;

    @GetMapping("")
    public List<UserAdminDto> listUsers()
    {
        return userAdminService.listUsers();
    }

    @GetMapping("/me")
    public UserAdminDto currentUser()
    {
        return userAdminService.currentUser();
    }

    @PostMapping("")
    @Transactional
    public UserAdminDto createUser(@RequestBody CreateUserDTO dto)
    {
        UserAdminDto saved = userAdminService.createUser(dto);
        auditRecorder.success("USER_CREATED", "ACCOUNT", "CRITICAL", "USER",
            saved.username(), saved.username(), null, saved,
            Map.of("authorities", saved.authorities()));
        return saved;
    }

    @PutMapping("/{username}")
    @Transactional
    public UserAdminDto updateUser(@PathVariable String username, @RequestBody UpdateUserDTO dto)
    {
        UserAdminDto before = findUser(username);
        UserAdminDto updated = userAdminService.updateUser(username, dto);
        auditRecorder.success("USER_UPDATED", "ACCOUNT", "CRITICAL", "USER",
            username, username, before, updated,
            Map.of("passwordReset", dto.password() != null && !dto.password().isBlank()));
        return updated;
    }

    @DeleteMapping("/{username}")
    @Transactional
    public void deleteUser(@PathVariable String username)
    {
        UserAdminDto before = findUser(username);
        userAdminService.deleteUser(username);
        auditRecorder.success("USER_DELETED", "ACCOUNT", "CRITICAL", "USER",
            username, username, before, null);
    }

    @PostMapping("/{username}/enable")
    @Transactional
    public UserAdminDto enableUser(@PathVariable String username)
    {
        UserAdminDto before = findUser(username);
        UserAdminDto updated = userAdminService.enableUser(username);
        auditRecorder.success("USER_ENABLED", "ACCOUNT", "CRITICAL", "USER",
            username, username, before, updated);
        return updated;
    }

    @PostMapping("/{username}/disable")
    @Transactional
    public UserAdminDto disableUser(@PathVariable String username)
    {
        UserAdminDto before = findUser(username);
        UserAdminDto updated = userAdminService.disableUser(username);
        auditRecorder.success("USER_DISABLED", "ACCOUNT", "CRITICAL", "USER",
            username, username, before, updated);
        return updated;
    }

    private UserAdminDto findUser(String username)
    {
        return userAdminService.listUsers().stream()
            .filter(user -> username.equals(user.username()))
            .findFirst()
            .orElse(null);
    }
}
