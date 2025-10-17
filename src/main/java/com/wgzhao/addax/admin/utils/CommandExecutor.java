package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 命令执行工具类。
 * 提供执行 shell 命令并返回标准化结果的方法。
 */
@Slf4j
public class CommandExecutor
{
    // 默认超时（秒）
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * 执行指定的 shell 命令，并返回执行结果。
     * 采用 ProcessBuilder + 并发消费 stdout/stderr + 超时保护的实现。
     *
     * @param command 要执行的 shell 命令字符串
     * @return TaskResultDto 执行结果对象，包含成功/失败信息及耗时
     */
    public static TaskResultDto executeWithResult(String command)
    {
        return executeWithResult(command, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 可设置超时的执行方法。
     *
     * @param command 要执行的 shell 命令字符串
     * @param timeoutSeconds 超时秒数（<=0 表示不超时）
     * @return TaskResultDto
     */
    public static TaskResultDto executeWithResult(String command, long timeoutSeconds)
    {
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<String> cmd;
        if (isWindows) {
            cmd = List.of("cmd.exe", "/c", command);
        } else {
            cmd = List.of("bash", "-c", command);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false); // 保持 stderr 分离
        // 使用通用 helper 执行
        return executeWithProcessBuilder(pb, timeoutSeconds, command);
    }

    /**
     * 新增：不走 shell 的重载（使用提供的命令与参数列表）。
     * @param cmd 命令及参数列表（例如: List.of("/usr/bin/rsync", "-av", "src/", "dst/")）
     * @return 执行结果（使用默认超时）
     */
    public static TaskResultDto executeWithResult(List<String> cmd) {
        return executeWithResult(cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 新增：不走 shell 的重载，允许指定超时（秒）。
     * 该方法直接使用传入的命令列表执行子进程，避免 shell 解析与注入风险。
     * @param cmd 命令与参数列表（不经过 shell）
     * @param timeoutSeconds 超时秒数（<=0 表示不超时）
     * @return TaskResultDto
     */
    public static TaskResultDto executeWithResult(List<String> cmd, long timeoutSeconds) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        String display = String.join(" ", cmd);
        return executeWithProcessBuilder(pb, timeoutSeconds, display);
    }

    // 提取的共享实现：接收 ProcessBuilder 并执行，返回 TaskResultDto
    private static TaskResultDto executeWithProcessBuilder(ProcessBuilder pb, long timeoutSeconds, String displayCmd) {
        long startAt = System.currentTimeMillis();

        ExecutorService ioPool = Executors.newFixedThreadPool(2);
        final AtomicReference<Process> processRef = new AtomicReference<>();
        try {
            Process started = pb.start();
            processRef.set(started);
            long pid = -1;
            try {
                pid = started.pid();
            } catch (UnsupportedOperationException ignored) {
            }
            log.info("Started process pid={} cmd={}", pid, displayCmd);

            // 异步读取 stdout
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
                StringBuilder out = new StringBuilder();
                Process p = processRef.get();
                if (p == null) return out.toString();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.append(line).append('\n');
                        // 同时把 stdout 以 info 级别打印
                        log.info(line);
                    }
                } catch (IOException e) {
                    log.warn("Error reading stdout: {}", e.getMessage());
                }
                return out.toString();
            }, ioPool);

            // 异步读取 stderr
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                StringBuilder err = new StringBuilder();
                Process p = processRef.get();
                if (p == null) return err.toString();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        err.append(line).append('\n');
                        log.error(line);
                    }
                } catch (IOException e) {
                    log.warn("Error reading stderr: {}", e.getMessage());
                }
                return err.toString();
            }, ioPool);

            boolean finished;
            Process pWait = processRef.get();
            if (pWait == null) {
                long duration = (System.currentTimeMillis() - startAt) / 1000;
                return TaskResultDto.failure("Failed to start process", duration);
            }

            if (timeoutSeconds > 0) {
                finished = pWait.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    // 超时
                    pWait.destroyForcibly();
                    // 尝试等待进程退出
                    pWait.onExit().join();
                    long duration = (System.currentTimeMillis() - startAt) / 1000;
                    String errMsg = "Process timed out after " + timeoutSeconds + " seconds";
                    // 尝试获取 stderr 内容（若未完成则等待短时间）
                    try {
                        String stderr = stderrFuture.get(200, TimeUnit.MILLISECONDS);
                        if (!stderr.isEmpty()) {
                            errMsg = stderr;
                        }
                    } catch (Exception ignored) {
                    }
                    return TaskResultDto.failure(errMsg, duration);
                }
            } else {
                // 不限时等待
                pWait.waitFor();
            }

            int exitCode = pWait.exitValue();
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();
            long duration = (System.currentTimeMillis() - startAt) / 1000;

            if (exitCode != 0) {
                String err = stderr.isEmpty() ? ("exit code: " + exitCode) : stderr;
                return TaskResultDto.failure(err, duration);
            } else {
                String outMsg = stdout.isEmpty() ? "SUCCESS" : stdout;
                return TaskResultDto.success(outMsg, duration);
            }
        }
        catch (IOException e) {
            long duration = (System.currentTimeMillis() - startAt) / 1000;
            log.error("execute command failed: {}", displayCmd, e);
            return TaskResultDto.failure(e.getMessage(), duration);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = (System.currentTimeMillis() - startAt) / 1000;
            Process p = processRef.get();
            if (p != null) {
                p.destroyForcibly();
            }
            return TaskResultDto.failure("Interrupted", duration);
        }
        finally {
            ioPool.shutdownNow();
        }
    }
}