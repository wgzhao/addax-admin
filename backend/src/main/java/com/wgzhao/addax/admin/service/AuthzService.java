package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.Roles;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Authoritative authorization decisions for the current request.
 *
 * <p>Roles come from the security context (the JWT filter re-reads them from the database on every
 * request). Source grants are loaded from the database with a short cache; a grant change invalidates
 * the cache for that account, and the 30-second TTL bounds the window on other nodes.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthzService
{
    private static final long GRANT_CACHE_TTL_MS = 30_000;

    private final UserSourceGrantService userSourceGrantService;
    private final EtlTableRepo etlTableRepo;
    private final ConcurrentHashMap<String, CachedGrants> grantCache = new ConcurrentHashMap<>();

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

    public boolean isGuest()
    {
        return Roles.GUEST.equals(currentRole());
    }

    /**
     * @return null when the role sees every source (admin and guest), otherwise the exact set of
     * readable source IDs. An empty set means the user has no grants and must see nothing.
     */
    public Set<Integer> readableSourceIds()
    {
        if (isAdmin() || isGuest()) {
            return null;
        }
        return currentGrants().keySet();
    }

    /**
     * @return null for admin (all sources), otherwise the sources with OPERATE.
     */
    public Set<Integer> operableSourceIds()
    {
        if (isAdmin()) {
            return null;
        }
        if (isGuest()) {
            return Set.of();
        }
        return currentGrants().entrySet().stream()
            .filter(entry -> entry.getValue() == UserSourceGrantService.AccessLevel.OPERATE)
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    }

    public Map<Integer, UserSourceGrantService.AccessLevel> currentGrants()
    {
        String username = currentUsername();
        if (username.isBlank()) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        CachedGrants cached = grantCache.get(username);
        if (cached != null && now - cached.loadedAt() < GRANT_CACHE_TTL_MS) {
            return cached.grants();
        }
        Map<Integer, UserSourceGrantService.AccessLevel> loaded =
            Map.copyOf(userSourceGrantService.load(username));
        grantCache.put(username, new CachedGrants(loaded, now));
        return loaded;
    }

    public void invalidateGrants(String username)
    {
        if (username != null && !username.isBlank()) {
            grantCache.remove(username);
        }
    }

    public boolean canReadSource(int sourceId)
    {
        if (isAdmin() || isGuest()) {
            return true;
        }
        return currentGrants().containsKey(sourceId);
    }

    public boolean canOperateSource(int sourceId)
    {
        if (isAdmin()) {
            return true;
        }
        if (isGuest()) {
            return false;
        }
        return currentGrants().get(sourceId) == UserSourceGrantService.AccessLevel.OPERATE;
    }

    /**
     * Connection details, rendered job JSON and raw logs are operational reads, not metadata reads.
     */
    public boolean canViewOperationalSource(int sourceId)
    {
        return canOperateSource(sourceId);
    }

    public void requireAdmin()
    {
        if (!isAdmin()) {
            throw new ApiException(403, "仅管理员可执行该操作");
        }
    }

    public void requireSourceRead(int sourceId)
    {
        if (!canReadSource(sourceId)) {
            throw new ApiException(403, "无权访问该采集源");
        }
    }

    public void requireSourceOperate(int sourceId)
    {
        if (!canOperateSource(sourceId)) {
            throw new ApiException(403, "无权操作该采集源");
        }
    }

    public Integer tableSid(long tableId)
    {
        return etlTableRepo.findById(tableId)
            .map(EtlTable::getSid)
            .orElse(null);
    }

    public void requireTableRead(long tableId)
    {
        Integer sid = tableSid(tableId);
        if (sid == null) {
            throw new ApiException(404, "Table not found");
        }
        requireSourceRead(sid);
    }

    public void requireTableOperate(long tableId)
    {
        Integer sid = tableSid(tableId);
        if (sid == null) {
            throw new ApiException(404, "Table not found");
        }
        requireSourceOperate(sid);
    }

    private record CachedGrants(Map<Integer, UserSourceGrantService.AccessLevel> grants, long loadedAt)
    {
    }
}
