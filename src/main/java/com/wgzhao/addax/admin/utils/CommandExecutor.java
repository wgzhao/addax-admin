package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * 命令执行工具类
 */
@Slf4j
public class CommandExecutor
{
    public static TaskResultDto executeWithResult(String command)
    {
        long startAt = System.currentTimeMillis();
        try {
            Process process = Runtime.getRuntime().exec(new String[] {"bash", "-c", command});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                StringBuilder errLine = new StringBuilder();
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errLine.append(line).append("\n");
                    }
                }
                return TaskResultDto.failure(errLine.toString(), System.currentTimeMillis() - startAt);
            }
            else {
                return TaskResultDto.success("SUCCESS", System.currentTimeMillis() - startAt);
            }
        }
        catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("execute command failed: {}", command, e);
            return TaskResultDto.failure(e.getMessage(), System.currentTimeMillis() - startAt);
        }
    }
}