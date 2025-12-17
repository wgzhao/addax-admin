package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.LeaderElection;
import com.wgzhao.addax.admin.repository.LeaderElectionRepo;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Database-based leader election service.
 *
 * Uses a single row in table leader_election with id=1 as the coordination lock.
 */
@Service
@Slf4j
public class LeaderElectionService {

    private final LeaderElectionRepo leaderElectionRepo;

    /** Unique identifier for this node. */
    @Getter
    private final String nodeId;

    /** Whether this node currently believes it is the leader. */
    @Getter
    private volatile boolean leader;

    /** Lock TTL and heartbeat, can later be externalized to config if needed. */
    private final Duration lockTtl = Duration.ofSeconds(20);

    // simple listeners to be notified on leadership changes
    public interface LeadershipListener {
        void onBecameLeader();
        void onLostLeader();
    }

    private final List<LeadershipListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(LeadershipListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(LeadershipListener listener) {
        listeners.remove(listener);
    }

    public LeaderElectionService(LeaderElectionRepo leaderElectionRepo,
                                 @Value("${addax.node-id:}") String configuredNodeId) {
        this.leaderElectionRepo = leaderElectionRepo;
        // Prefer configured node id; if not set, derive a stable identifier from machine information.
        this.nodeId = resolveNodeId(configuredNodeId);
        log.info("LeaderElectionService initialized with nodeId={}", this.nodeId);
    }

    /**
     * Resolve a stable node id.
     * Priority:
     * 1) Explicit configuration (addax.node-id)
     * 2) hostname + primary MAC address, hashed
     * 3) hostname + OS/user fingerprint, hashed
     * 4) random UUID (last-resort, logs a warning)
     */
    private String resolveNodeId(String configuredNodeId) {
        if (configuredNodeId != null && !configuredNodeId.isBlank()) {
            String id = configuredNodeId.trim();
            log.info("Using configured addax.node-id: {}", id);
            return id;
        }

        // Try hostname + MAC address
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            String hostname = localHost.getHostName();
            java.net.NetworkInterface ni = java.net.NetworkInterface.getByInetAddress(localHost);
            byte[] mac = ni != null ? ni.getHardwareAddress() : null;

            if (hostname != null && !hostname.isBlank() && mac != null && mac.length > 0) {
                StringBuilder macStr = new StringBuilder();
                for (byte b : mac) {
                    macStr.append(String.format("%02X", b));
                }
                String raw = hostname + "-" + macStr;
                String hashed = sha256Hex(raw).substring(0, 16); // short but stable
                String id = "hostmac-" + hashed;
                log.info("Derived node id from hostname and MAC: hostname={}, id={}", hostname, id);
                return id;
            }
        } catch (Exception e) {
            log.warn("Failed to derive node id from hostname/MAC, will try OS fingerprint", e);
        }

        // Fallback: hostname + OS fingerprint
        try {
            String hostname;
            try {
                java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
                hostname = localHost.getHostName();
            } catch (Exception e) {
                hostname = "unknown-host";
            }
            String osName = System.getProperty("os.name", "unknown-os");
            String osArch = System.getProperty("os.arch", "unknown-arch");
            String userName = System.getProperty("user.name", "unknown-user");
            String raw = hostname + "-" + osName + "-" + osArch + "-" + userName;
            String hashed = sha256Hex(raw).substring(0, 16);
            String id = "hostos-" + hashed;
            log.info("Derived node id from hostname/OS fingerprint: hostname={}, id={}", hostname, id);
            return id;
        } catch (Exception e) {
            log.warn("Failed to derive node id from hostname/OS fingerprint, will fall back to random UUID", e);
        }

        // Last-resort: random UUID (not stable across restarts). Log a strong warning.
        String id = "uuid-" + UUID.randomUUID();
        log.warn("Using random UUID-based node id (not stable across restarts): {}. " +
                "For production, configure a fixed addax.node-id.", id);
        return id;
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256 hash", e);
        }
    }

    @PostConstruct
    public void init() {
        try {
            // Try to acquire leadership at startup; failures will be retried in heartbeat
            tryAcquireLeadership();
        } catch (Exception e) {
            log.error("Failed to acquire leadership on startup, will retry in heartbeat", e);
        }
    }

    /**
     * Heartbeat task: periodically renew or try to acquire leadership.
     */
    @Scheduled(fixedDelayString = "5000")
    public void heartbeat() {
        try {
            if (leader) {
                renewLockIfStillLeader();
            } else {
                tryAcquireLeadership();
            }
        } catch (Exception e) {
            log.warn("Leader election heartbeat error", e);
        }
    }

    @Transactional
    public void tryAcquireLeadership() {
        Instant now = Instant.now();
        Instant newExpiry = now.plus(lockTtl);

        LeaderElection lock = leaderElectionRepo.findById(1L).orElse(null);
        if (lock == null) {
            lock = new LeaderElection();
            lock.setId(1L);
            lock.setNodeId(nodeId);
            lock.setExpiresAt(newExpiry);
            lock.setUpdatedAt(now);
            leaderElectionRepo.save(lock);
            becomeLeader();
            return;
        }

        // If current lock is expired, take over.
        if (lock.getExpiresAt().isBefore(now)) {
            log.info("Existing leader lock expired (nodeId={}, expiresAt={}), this node {} will take over",
                    lock.getNodeId(), lock.getExpiresAt(), nodeId);
            lock.setNodeId(nodeId);
            lock.setExpiresAt(newExpiry);
            lock.setUpdatedAt(now);
            leaderElectionRepo.save(lock);
            becomeLeader();
        } else if (nodeId.equals(lock.getNodeId())) {
            // We are already the recorded leader but maybe our local flag is false (e.g., restart)
            log.info("This node {} is already recorded as leader, refreshing lock", nodeId);
            lock.setExpiresAt(newExpiry);
            lock.setUpdatedAt(now);
            leaderElectionRepo.save(lock);
            becomeLeader();
        } else {
            // Another valid leader exists
            if (leader) {
                loseLeadership();
            }
        }
    }

    @Transactional
    public void renewLockIfStillLeader() {
        Instant now = Instant.now();
        Instant newExpiry = now.plus(lockTtl);

        LeaderElection lock = leaderElectionRepo.findById(1L).orElse(null);
        if (lock == null) {
            // No lock row? Re-create as leader.
            log.warn("Leader lock row missing, recreating as leader: {}", nodeId);
            lock = new LeaderElection();
            lock.setId(1L);
            lock.setNodeId(nodeId);
            lock.setExpiresAt(newExpiry);
            lock.setUpdatedAt(now);
            leaderElectionRepo.save(lock);
            becomeLeader();
            return;
        }

        if (!nodeId.equals(lock.getNodeId())) {
            // Another node took leadership
            log.info("Node {} lost leadership to {}", nodeId, lock.getNodeId());
            loseLeadership();
            return;
        }

        if (lock.getExpiresAt().isBefore(now)) {
            // Our own lock expired before we could renew; step down and let acquisition logic handle next time.
            log.warn("Leader lock for node {} expired before renewal (expiresAt={})", nodeId, lock.getExpiresAt());
            loseLeadership();
            return;
        }

        lock.setExpiresAt(newExpiry);
        lock.setUpdatedAt(now);
        leaderElectionRepo.save(lock);
    }

    private void becomeLeader() {
        if (!this.leader) {
            this.leader = true;
            log.info("Node {} became leader", nodeId);
            for (LeadershipListener listener : listeners) {
                try {
                    listener.onBecameLeader();
                } catch (Exception e) {
                    log.warn("Error notifying listener onBecameLeader", e);
                }
            }
        }
    }

    private void loseLeadership() {
        if (this.leader) {
            this.leader = false;
            log.info("Node {} lost leadership", nodeId);
            for (LeadershipListener listener : listeners) {
                try {
                    listener.onLostLeader();
                } catch (Exception e) {
                    log.warn("Error notifying listener onLostLeader", e);
                }
            }
        }
    }
}
