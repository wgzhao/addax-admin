package com.wgzhao.addax.admin.common;

import java.util.Collection;
import java.util.Locale;

/**
 * The three roles the platform supports. Unknown values fail closed to guest so a misconfigured
 * authority can never grant more than read-only metadata access.
 */
public final class Roles
{
    public static final String ADMIN = "admin";
    public static final String USER = "user";
    public static final String GUEST = "guest";

    private Roles()
    {
    }

    public static String normalize(String authority)
    {
        if (authority == null || authority.isBlank()) {
            return GUEST;
        }
        String normalized = authority.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("role_")) {
            normalized = normalized.substring(5);
        }
        return switch (normalized) {
            case ADMIN -> ADMIN;
            case USER -> USER;
            default -> GUEST;
        };
    }

    /**
     * Pick the highest role when an account has more than one authority row. A user is expected to
     * have exactly one role; this keeps legacy or hand-edited rows deterministic.
     */
    public static String highest(Collection<String> authorities)
    {
        boolean admin = false;
        boolean user = false;
        if (authorities != null) {
            for (String authority : authorities) {
                String role = normalize(authority);
                if (ADMIN.equals(role)) {
                    admin = true;
                }
                else if (USER.equals(role)) {
                    user = true;
                }
            }
        }
        if (admin) {
            return ADMIN;
        }
        return user ? USER : GUEST;
    }
}
