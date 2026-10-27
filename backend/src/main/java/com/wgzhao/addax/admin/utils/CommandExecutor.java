package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;


/**
 * 命令执行工具类。
 * 提供执行 shell 命令并返回标准化结果的方法。
 */
@Slf4j
public class CommandExecutor
{
    /**
     * Start a process with explicit command args and return the Process object.
     */
    public static Process startProcess(List<String> commandArgs)
        throws IOException
    {
        ProcessBuilder pb = new ProcessBuilder(commandArgs);
        pb.redirectErrorStream(true);
        return pb.start();
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
        return waitForProcessWithResult(process, timeoutSeconds, command, null);
    }

    /**
     * Wait for an already started process, checking {@code fenceReason} while it runs.
     *
     * <p>A non-null reason means this instance no longer owns the queue row (or can no longer prove
     * it does). The process tree is then destroyed before returning: a process that lost ownership
     * must never keep writing to the target while a successor runs the same table.</p>
     */
    public static TaskResultDto waitForProcessWithResult(Process process, long timeoutSeconds, String command,
        Supplier<String> fenceReason)
    {
        long startAt = System.currentTimeMillis();
        // Poll both the process and the ownership fence; 2s bounds how long a fenced process can
        // keep writing after ownership loss while staying cheap when nothing changes.
        final long pollMillis = 2000;
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
            while (true) {
                String fenced = fenceReason == null ? null : fenceReason.get();
                if (fenced != null) {
                    log.warn("Process pid={} lost queue ownership ({}), destroying its tree", pid, fenced);
                    ProcessGuard.destroyTree(process);
                    long duration = (System.currentTimeMillis() - startAt) / 1000;
                    return TaskResultDto.failure("Ownership lost: " + fenced, duration);
                }

                if (timeoutSeconds > 0) {
                    long remaining = timeoutSeconds * 1000 - (System.currentTimeMillis() - startAt);
                    if (remaining <= 0) {
                        log.warn("Process pid={} timed out after {} seconds, destroying tree...", pid, timeoutSeconds);
                        ProcessGuard.destroyTree(process);
                        long duration = (System.currentTimeMillis() - startAt) / 1000;
                        return TaskResultDto.failure("Process timed out after " + timeoutSeconds + " seconds", duration);
                    }
                    finished = process.waitFor(Math.min(pollMillis, remaining), TimeUnit.MILLISECONDS);
                }
                else {
                    finished = process.waitFor(pollMillis, TimeUnit.MILLISECONDS);
                }
                if (finished) {
                    break;
                }
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
            // Shutdown/stop must not leave the addax tree behind: the wrapper's parent-death
            // watchdog covers a crash, but a graceful stop has to be prompt.
            ProcessGuard.destroyTree(process);
            long duration = (System.currentTimeMillis() - startAt) / 1000;
            return TaskResultDto.failure("Interrupted", duration);
        }
        catch (Exception e) {
            ProcessGuard.destroyTree(process);
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
}
