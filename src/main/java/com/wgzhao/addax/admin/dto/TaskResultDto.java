package com.wgzhao.addax.admin.dto;


public record TaskResultDto(boolean success, String message, long durationSeconds)
{
    public static TaskResultDto success(String message, long durationSeconds) {
        return new TaskResultDto(true, message, durationSeconds);
    }

    public static TaskResultDto failure(String message, long durationSeconds) {
        return new TaskResultDto(false, message, durationSeconds);
    }
}

