package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.CreateUserDTO;
import com.wgzhao.addax.admin.dto.UpdateUserDTO;
import com.wgzhao.addax.admin.dto.UserAdminDto;
import com.wgzhao.addax.admin.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController
{
    private final UserAdminService userAdminService;

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
    public UserAdminDto createUser(@RequestBody CreateUserDTO dto)
    {
        return userAdminService.createUser(dto);
    }

    @PutMapping("/{username}")
    public UserAdminDto updateUser(@PathVariable String username, @RequestBody UpdateUserDTO dto)
    {
        return userAdminService.updateUser(username, dto);
    }

    @DeleteMapping("/{username}")
    public void deleteUser(@PathVariable String username)
    {
        userAdminService.deleteUser(username);
    }

    @PostMapping("/{username}/enable")
    public UserAdminDto enableUser(@PathVariable String username)
    {
        return userAdminService.enableUser(username);
    }

    @PostMapping("/{username}/disable")
    public UserAdminDto disableUser(@PathVariable String username)
    {
        return userAdminService.disableUser(username);
    }
}
