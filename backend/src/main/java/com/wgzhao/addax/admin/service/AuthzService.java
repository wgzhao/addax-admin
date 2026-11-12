package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.Roles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resolves the current account and role from the security context. Phase 1 extends this with the
 * source grant checks; keeping the role lookup in one place avoids scattering role strings across
 * controllers and services.
 */
@Service
public class AuthzService
{
    public String currentUsername()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "" : String.valueOf(authentication.getName());
    }

    public String currentRole()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return Roles.GUEST;
        }
        return Roles.highest(authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList());
    }

    public boolean isAdmin()
    {
        return Roles.ADMIN.equals(currentRole());
    }
}
