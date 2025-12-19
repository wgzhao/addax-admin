package com.wgzhao.addax.admin.service;

/**
 * Deprecated placeholder.
 *
 * Leader election via DB has been removed in favor of Redis-based arbitration.
 * This class intentionally provides no runtime behavior to avoid scheduled heartbeats.
 */
@Deprecated
public final class LeaderElectionService {
    private LeaderElectionService() {
        // utility class - not instantiable
    }

    /** Placeholder listener interface retained for source-compatibility with older code. */
    public interface LeadershipListener {
        default void onBecameLeader() {}
        default void onLostLeader() {}
    }
}
