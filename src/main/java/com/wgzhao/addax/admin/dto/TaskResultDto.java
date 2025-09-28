package com.wgzhao.addax.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class TaskResultDto
{
    private final boolean success;
    private final String message;
    private final long durationSeconds;

    public static TaskResultDto success(String message, long durationSeconds) {
        return new TaskResultDto(true, message, durationSeconds);
    }

    public static TaskResultDto failure(String message, long durationSeconds)
    {
        return new TaskResultDto(false, message, durationSeconds);
    }
}

