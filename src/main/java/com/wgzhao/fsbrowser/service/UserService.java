package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.User;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {
    User save(User user);
}
