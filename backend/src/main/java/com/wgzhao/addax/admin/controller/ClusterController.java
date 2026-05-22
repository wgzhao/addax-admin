package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.redis.WorkerHeartbeatService;
import com.wgzhao.addax.admin.redis.WorkerHeartbeatService.WorkerInfo;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Cluster status API — exposes alive worker nodes and master identity.
 */
@RestController
@RequestMapping("/cluster")
@AllArgsConstructor
public class ClusterController
{
    private final WorkerHeartbeatService heartbeatService;
    private final MasterElectionService electionService;

    /**
     * Returns all alive worker nodes with their current state.
     * The master node is identified via masterInstanceId in the response.
     */
    @GetMapping("/nodes")
    public ResponseEntity<Map<String, Object>> nodes()
    {
        List<WorkerInfo> workers = heartbeatService.getAliveWorkers();
        String masterInstanceId = electionService.getMasterInstanceId();

        List<Map<String, Object>> nodes = workers.stream()
            .map(w -> {
                boolean isMaster = w.instanceId().equals(masterInstanceId);
                return Map.<String, Object>of(
                    "instanceId", w.instanceId(),
                    "host", w.host(),
                    "role", isMaster ? "MASTER/WORKER" : "WORKER",
                    "running", w.running(),
                    "availableSlots", w.availableSlots(),
                    "concurrentLimit", w.concurrentLimit(),
                    "weight", w.weight(),
                    "sourceRunning", w.sourceRunning(),
                    "lastSeen", w.lastSeen() != null ? w.lastSeen().toString() : null,
                    "online", true
                );
            })
            .sorted((a, b) -> {
                // master first
                boolean aMaster = "MASTER".equals(a.get("role"));
                boolean bMaster = "MASTER".equals(b.get("role"));
                if (aMaster && !bMaster) return -1;
                if (!aMaster && bMaster) return 1;
                return a.get("instanceId").toString().compareTo(b.get("instanceId").toString());
            })
            .toList();

        return ResponseEntity.ok(Map.of(
            "nodes", nodes,
            "masterInstanceId", masterInstanceId != null ? masterInstanceId : "",
            "totalNodes", nodes.size(),
            "timestamp", Instant.now().toString()
        ));
    }
}
