package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 命令执行工具类。
 * 提供执行 shell 命令并返回标准化结果的方法。
 */
@Slf4j
public class CommandExecutor {
    // 轻量级输出吞噬线程，避免子进程因缓冲区满而阻塞
    private static Thread startDiscarder(InputStream in) {
        Thread t = new Thread(() -> {
            try (in; OutputStream nullOut = OutputStream.nullOutputStream()) {
                in.transferTo(nullOut);
            } catch (IOException ignored) {
            }
        }, "cmd-out-discarder");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * 可设置超时的执行方法。
     *
     * @param command        要执行的 shell 命令字符串
     * @param timeoutSeconds 超时秒数（<=0 表示不超时）
     * @return TaskResultDto
     */
    public static TaskResultDto executeWithResult(String command, long timeoutSeconds) {
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<String> cmd = isWindows ? List.of("cmd.exe", "/c", command) : List.of("bash", "-c", command);

        long startAt = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            // 合并 stderr 到 stdout，减少线程数量
            pb.redirectErrorStream(true);
            Process process = pb.start();
            long pid;
            try {
                pid = process.pid();
            } catch (UnsupportedOperationException e) {
                pid = -1;
            }
            log.info("Started process pid={} cmd={}", pid, command);

            // 启动吞噬线程，防止输出缓冲阻塞导致子进程挂起
            Thread discarder = startDiscarder(process.getInputStream());

            boolean finished;
            if (timeoutSeconds > 0) {
                finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    log.warn("Process pid={} timed out after {} seconds, destroying...", pid, timeoutSeconds);
                    process.destroyForcibly();
                    process.onExit().join();
                    long duration = (System.currentTimeMillis() - startAt) / 1000;
                    return TaskResultDto.failure("Process timed out after " + timeoutSeconds + " seconds", duration);
                }
            } else {
                process.waitFor();
            }

            // 进程结束后，尽量等待吞噬线程退出（短暂等待，防止资源泄漏）
            try {
                discarder.join(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            int exitCode = process.exitValue();
            long duration = (System.currentTimeMillis() - startAt) / 1000;
            if (exitCode != 0) {
                return TaskResultDto.failure("exit code: " + exitCode, duration);
            }
            return TaskResultDto.success("", duration);
        } catch (IOException e) {
            long duration = (System.currentTimeMillis() - startAt) / 1000;
            log.error("execute command failed: {}", command, e);
            return TaskResultDto.failure(e.getMessage(), duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = (System.currentTimeMillis() - startAt) / 1000;
            return TaskResultDto.failure("Interrupted", duration);
        }
    }

    // 便捷重载：不设置超时
    public static TaskResultDto executeWithResult(String command) {
        return executeWithResult(command, 0);
    }
}
