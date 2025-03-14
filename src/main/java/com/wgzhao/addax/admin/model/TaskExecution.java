package com.wgzhao.addax.admin.model;

import lombok.Data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "task_execution")
public class TaskExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "collect_id", nullable = false)
    private Long collectId;
    
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "duration")
    private Integer duration;
    
    @Column(name = "exec_status", nullable = false)
    private String execStatus;
    
    @Column(name = "total_records")
    private Long totalRecords;
    
    @Column(name = "success_records")
    private Long successRecords;
    
    @Column(name = "failed_records")
    private Long failedRecords;
    
    @Column(name = "rejected_records")
    private Long rejectedRecords;
    
    @Column(name = "bytes_speed")
    private Long bytesSpeed;
    
    @Column(name = "records_speed")
    private Long recordsSpeed;
    
    @Column(name = "log_path")
    private String logPath;
    
    @Column(name = "execution_json", columnDefinition = "JSON")
    private String executionJson;
    
    @Column(name = "trigger_type")
    private String triggerType;
}