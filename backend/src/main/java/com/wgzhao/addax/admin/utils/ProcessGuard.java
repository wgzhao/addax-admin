package com.wgzhao.addax.admin.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Launches addax under a wrapper that ties the child's lifetime to the owning admin JVM.
 *
 * <p>The queue row is the only ownership fence the database can express, so a process that outlives
 * its owner would keep writing to the target while a restarted admin re-dispatches the same table.
 * The wrapper therefore kills the addax process group when any of these happens:</p>
 * <ol>
 *   <li>the admin JVM process disappears (crash, OOM kill, service restart), or</li>
 *   <li>the lease heartbeat file stops advancing, i.e. the JVM can no longer prove it still owns
 *       the queue row (hung JVM, database partition), or</li>
 *   <li>the wrapper itself is asked to terminate (the JVM's shutdown path).</li>
 * </ol>
 *
 * <p>The heartbeat file is written by the lease renewer only after a successful database renewal.
 * Its staleness threshold must stay below the queue lease TTL so the child is dead before the row
 * can be re-dispatched.</p>
 */
@Slf4j
public final class ProcessGuard
{
    /**
     * How long the lease heartbeat file may stay unchanged before the wrapper kills addax.
     * Must be greater than the renewal interval (20s) and smaller than the queue lease (120s).
     */
    public static final int LEASE_FRESHNESS_SECONDS = 75;

    private static final String WRAPPER_FILE_NAME = ".addax-child-wrapper.sh";
    private static final String LEASE_DIR_NAME = ".runs";
    private static final String NO_LEASE_FILE = "-";

    private static final String WRAPPER_RESOURCE = "/scripts/addax-child-wrapper.sh";

    private static volatile Path wrapperPath;

    private ProcessGuard()
    {
    }

    /**
     * A fresh heartbeat file for one execution. A UUID suffix keeps a successor run from sharing
     * (and thereby reviving) the file of an older run of the same queue row.
     */
    public static Path newLeaseFile(long jobId) throws IOException
    {
        Path dir = leaseDir();
        Files.createDirectories(dir);
        return dir.resolve("job-" + jobId + "-" + UUID.randomUUID() + ".lease");
    }

    /**
     * Publish a fresh lease timestamp. Written through a temporary file + atomic move so the
     * wrapper never observes a truncated/empty value and kills a healthy run.
     */
    public static void touchLease(Path leaseFile) throws IOException
    {
        String mode = clockMode();
        long value = clockValue(mode);
        Path tmp = Files.createTempFile(leaseFile.getParent(), ".lease-", ".tmp");
        try {
            Files.writeString(tmp, Long.toString(value), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, leaseFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, leaseFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static void deleteLease(Path leaseFile)
    {
        if (leaseFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(leaseFile);
        }
        catch (IOException e) {
            log.debug("Failed to delete lease file {}", leaseFile, e);
        }
    }

    /**
     * Remove heartbeat files left by a previous JVM. Any wrapper still watching one of them will
     * see it as stale and terminate its orphaned addax child.
     */
    public static void cleanupLeaseFiles()
    {
        try (Stream<Path> files = Files.list(leaseDir())) {
            int removed = 0;
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".lease")).toList()) {
                try {
                    Files.deleteIfExists(file);
                    removed++;
                }
                catch (IOException e) {
                    log.warn("Failed to delete stale lease file {}", file, e);
                }
            }
            if (removed > 0) {
                log.info("Removed {} stale addax lease heartbeat file(s)", removed);
            }
        }
        catch (IOException e) {
            log.debug("No lease directory to clean: {}", e.getMessage());
        }
    }

    /**
     * Start {@code commandArgs} under the wrapper. {@code leaseFile} may be null for executions that
     * are not queue-owned; those still get the crash (parent-death) protection.
     */
    public static Process start(List<String> commandArgs, Path leaseFile) throws IOException
    {
        if (isWindows()) {
            // The wrapper is a POSIX shell script; Windows deployments keep the old behaviour.
            log.warn("ProcessGuard wrapper is unavailable on Windows; addax runs without orphan protection");
            ProcessBuilder pb = new ProcessBuilder(commandArgs);
            pb.redirectErrorStream(true);
            return pb.start();
        }

        Path wrapper = ensureWrapper();
        List<String> guarded = new ArrayList<>(commandArgs.size() + 7);
        guarded.add("/bin/sh");
        guarded.add(wrapper.toString());
        guarded.add(Long.toString(ProcessHandle.current().pid()));
        guarded.add(parentStartTicks());
        guarded.add(clockMode());
        guarded.add(leaseFile == null ? NO_LEASE_FILE : leaseFile.toString());
        guarded.add(Integer.toString(LEASE_FRESHNESS_SECONDS));
        guarded.addAll(commandArgs);

        ProcessBuilder pb = new ProcessBuilder(guarded);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /**
     * Terminate the wrapper and its whole addax tree. Descendants are killed first so the process
     * that actually writes to the target cannot survive its parent.
     */
    public static void destroyTree(Process process)
    {
        if (process == null) {
            return;
        }
        ProcessHandle root = process.toHandle();
        List<ProcessHandle> descendants = root.descendants().toList();
        List<ProcessHandle> deepestFirst = descendants.stream()
            .sorted(Comparator.comparingLong(ProcessGuard::depth).reversed())
            .toList();
        deepestFirst.forEach(handle -> destroyQuietly(handle, false));
        destroyQuietly(root, false);
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        deepestFirst.forEach(handle -> destroyQuietly(handle, true));
        destroyQuietly(root, true);
    }

    private static void destroyQuietly(ProcessHandle handle, boolean force)
    {
        try {
            if (!handle.isAlive()) {
                return;
            }
            if (force) {
                handle.destroyForcibly();
            }
            else {
                handle.destroy();
            }
        }
        catch (Exception e) {
            log.debug("Failed to stop pid={} force={}", handle.pid(), force, e);
        }
    }

    private static long depth(ProcessHandle handle)
    {
        long d = 0;
        java.util.Optional<ProcessHandle> p = handle.parent();
        while (p.isPresent()) {
            d++;
            p = p.get().parent();
        }
        return d;
    }

    private static Path leaseDir()
    {
        return appHome().resolve("job").resolve(LEASE_DIR_NAME);
    }

    private static Path appHome()
    {
        String home = System.getProperty("app.home");
        if (home == null || home.isBlank()) {
            home = System.getProperty("user.dir");
        }
        return Path.of(home);
    }

    private static synchronized Path ensureWrapper() throws IOException
    {
        Path wrapper = appHome().resolve("job").resolve(WRAPPER_FILE_NAME);
        if (wrapperPath != null && Files.exists(wrapperPath) && wrapperPath.equals(wrapper)) {
            return wrapperPath;
        }
        Files.createDirectories(wrapper.getParent());
        String packaged = readWrapperResource();
        String current = Files.exists(wrapper) ? Files.readString(wrapper, StandardCharsets.UTF_8) : null;
        if (!packaged.equals(current)) {
            Files.writeString(wrapper, packaged, StandardCharsets.UTF_8);
            log.info("Wrote addax child wrapper to {}", wrapper);
        }
        wrapperPath = wrapper;
        return wrapper;
    }

    /**
     * The wrapper lives as a normal shell file so it can be linted and reviewed as shell, and is
     * extracted verbatim at runtime. A missing resource is fatal: running addax unguarded would
     * silently reintroduce the orphan-process duplicate this class exists to prevent.
     */
    private static String readWrapperResource() throws IOException
    {
        try (InputStream in = ProcessGuard.class.getResourceAsStream(WRAPPER_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing classpath resource " + WRAPPER_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String clockMode()
    {
        return Files.isReadable(Path.of("/proc/uptime")) ? "uptime" : "epoch";
    }

    private static long clockValue(String mode) throws IOException
    {
        if ("uptime".equals(mode)) {
            String uptime = Files.readString(Path.of("/proc/uptime"), StandardCharsets.UTF_8).trim();
            int dot = uptime.indexOf('.');
            return Long.parseLong(dot < 0 ? uptime : uptime.substring(0, dot));
        }
        return System.currentTimeMillis() / 1000;
    }

    /**
     * Field 22 of /proc/&lt;pid&gt;/stat. The wrapper compares it with the live value so a recycled
     * parent pid cannot keep an orphan alive. Empty when /proc is unavailable.
     */
    private static String parentStartTicks()
    {
        try {
            String stat = Files.readString(Path.of("/proc/self/stat"), StandardCharsets.UTF_8).trim();
            String rest = stat.substring(stat.lastIndexOf(") ") + 2);
            String[] fields = rest.split(" ");
            // rest starts at field 3 (state); starttime is field 22 -> index 19.
            return fields.length > 19 ? fields[19] : "";
        }
        catch (Exception e) {
            log.warn("Cannot read /proc/self/stat; parent pid reuse protection is reduced", e);
            return "";
        }
    }

    private static boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
