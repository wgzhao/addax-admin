package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 命令执行工具类。
 * 提供执行 shell 命令并返回标准化结果的方法。
 */
@Slf4j
public class CommandExecutor
{
    // 轻量级输出吞噬线程，避免子进程因缓冲区满而阻塞
    private static Thread startDiscarder(InputStream in)
    {
        Thread t = new Thread(() -> {
            try (in; OutputStream nullOut = OutputStream.nullOutputStream()) {
                in.transferTo(nullOut);
            }
            catch (IOException ignored) {
            }
        }, "cmd-out-discarder");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Start a process for the given shell command and return the Process object.
     */
    public static Process startProcess(String command)
        throws IOException
    {
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<String> cmd = isWindows ? List.of("cmd.exe", "/c", command) : List.of("bash", "-c", command);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /**
     * Wait for an already started process and collect output, supporting timeout.
     * Returns TaskResultDto similar to executeWithResult.
     */
    public static TaskResultDto waitForProcessWithResult(Process process, long timeoutSeconds, String command)
    {
        long startAt = System.currentTimeMillis();
        try {
            long pid;
            try {
                pid = process.pid();
            }
            catch (UnsupportedOperationException e) {
                pid = -1;
            }
            log.info("Started process pid={} cmd={}", pid, command);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thread collector = new Thread(() -> {
                try (InputStream in = process.getInputStream()) {
                    in.transferTo(buffer);
                }
                catch (IOException ignored) {
                }
            }, "cmd-out-collector");
            collector.setDaemon(true);
            collector.start();

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
            }
            else {
                process.waitFor();
            }

            try {
                collector.join(1000);
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            int exitCode = process.exitValue();
            long duration = (System.currentTimeMillis() - startAt) / 1000;
            if (exitCode != 0) {
                String output = buffer.toString(StandardCharsets.UTF_8);
                String message = output.isEmpty()
                    ? ("exit code: " + exitCode)
                    : ("exit code: " + exitCode + ", output:\n" + output);
                return TaskResultDto.failure(message, duration);
            }
            return TaskResultDto.success("", duration);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = (System.currentTimeMillis() - startAt) / 1000;
            return TaskResultDto.failure("Interrupted", duration);
        }
        catch (Exception e) {
            long duration = (System.currentTimeMillis() - startAt) / 1000;
            log.error("execute command failed: {}", command, e);
            return TaskResultDto.failure(e.getMessage(), duration);
        }
    }

    /**
     * 可设置超时的执行方法。保留兼容性实现（内部直接启动并等待）。
     */
    public static TaskResultDto executeWithResult(String command, long timeoutSeconds)
    {
        try {
            Process p = startProcess(command);
            return waitForProcessWithResult(p, timeoutSeconds, command);
        }
        catch (IOException e) {
            log.error("execute command failed: {}", command, e);
            return TaskResultDto.failure(e.getMessage(), 0);
        }
    }

    // 便捷重载：不设置超时
    public static TaskResultDto executeWithResult(String command)
    {
        return executeWithResult(command, 0);
    }
}
