package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/**
 * Simple leader election row stored in the database.
 * A single row with a well-known ID is used as the global leader lock.
 */
@Entity
@Table(name = "leader_election")
@Data
public class LeaderElection {

    @Id
    @Column(name = "id")
    private Long id; // always 1L for the singleton row

    @Column(name = "node_id", nullable = false, length = 128)
    private String nodeId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
