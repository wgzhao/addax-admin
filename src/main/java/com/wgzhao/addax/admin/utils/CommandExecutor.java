package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * 命令执行工具类。
 * 提供执行 shell 命令并返回标准化结果的方法。
 */
@Slf4j
public class CommandExecutor
{
    /**
     * 执行指定的 shell 命令，并返回执行结果。
     * <p>
     * 标准输出内容会打印到控制台，错误输出内容会作为失败信息返回。
     * 返回的 TaskResultDto 包含执行状态和耗时（秒）。
     * </p>
     *
     * @param command 要执行的 shell 命令字符串
     * @return TaskResultDto 执行结果对象，包含成功/失败信息及耗时
     */
    public static TaskResultDto executeWithResult(String command)
    {
        long startAt = System.currentTimeMillis();
        try {
            // 使用 bash 执行命令，支持管道等 shell 特性
            Process process = Runtime.getRuntime().exec(new String[] {"bash", "-c", command});
            // 读取标准输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                // 读取错误输出
                StringBuilder errLine = new StringBuilder();
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errLine.append(line).append("\n");
                    }
                }
                return TaskResultDto.failure(errLine.toString(), (System.currentTimeMillis() - startAt)  / 1000);
            }
            else {
                return TaskResultDto.success("SUCCESS", (System.currentTimeMillis() - startAt) / 1000);
            }
        }
        catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("execute command failed: {}", command, e);
            return TaskResultDto.failure(e.getMessage(), (System.currentTimeMillis() - startAt) / 1000);
        }
    }
}