package com.wgzhao.addax.admin.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw addax output or an exception into the two text fields a failure fact keeps:
 * an actionable one-line summary and a bounded excerpt that survives raw-log retention.
 */
public final class FailureText
{
    private static final int DETAIL_MAX = 65_536;

    private FailureText() {}

    /**
     * Extracts a short, actionable reason from the addax run log. Exit codes such as 137 only
     * say the process was killed; the operator needs the error line or, when the process died
     * before addax reported one, at least a faithful description of how it ended.
     */
    public static String summarizeAddaxFailure(String logContent, String fallback)
    {
        if (logContent != null && !logContent.isBlank()) {
            // A big collection log can be hundreds of megabytes; the run-ending error is at the
            // tail, so scanning the last 64 KiB is enough and avoids materialising every line.
            String tail = logContent.length() > 65_536
                ? logContent.substring(logContent.length() - 65_536) : logContent;
            String[] lines = tail.split("\\R");
            // Walk backwards: the last ERROR/exception is the one that ended the run. Keep the
            // order the log printed them in.
            List<String> picked = new ArrayList<>(2);
            for (int i = lines.length - 1; i >= 0 && picked.size() < 2; i--) {
                String line = lines[i].strip();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.contains(" ERROR ") || line.contains("Exception") || line.startsWith("Caused by:")) {
                    picked.add(0, line);
                }
            }
            if (!picked.isEmpty()) {
                return truncateReason(String.join(" | ", picked));
            }
        }
        String message = fallback == null ? "" : fallback.strip();
        if (message.startsWith("Process timed out")) {
            return message;
        }
        if (message.startsWith("exit code: 137")) {
            return "进程被强制终止 (exit 137)，可能内存不足或被系统 OOM 杀死";
        }
        if (message.startsWith("exit code:")) {
            String lastLine = lastNonEmptyLine(message);
            return truncateReason(lastLine.isBlank() ? message : "Addax 非零退出，" + lastLine);
        }
        return truncateReason(message.isBlank() ? "Addax 非零退出，未捕获到错误输出" : message);
    }

    /**
     * Keeps the actionable tail of a run log in the failure fact, so the drawer still explains the
     * failure after the raw log has been purged. The raw log remains the full record.
     */
    public static String extractFailureExcerpt(String logContent, String fallback)
    {
        if (logContent == null || logContent.isBlank()) {
            return fallback;
        }
        int tailStart = Math.max(0, logContent.length() - 262_144);
        String tail = logContent.substring(tailStart);
        // Prefer the last ERROR line: stack frames follow it. "Caused by:" is the fallback for a
        // message that contains only a stack, and a bare "Exception" the last resort. Taking the
        // latest match of all markers would start inside "UnknownHostException" and keep no frames.
        int marker = tail.lastIndexOf(" ERROR ");
        if (marker < 0) {
            marker = tail.lastIndexOf("\nCaused by:");
        }
        if (marker < 0) {
            marker = tail.lastIndexOf("Exception");
        }
        if (marker < 0) {
            marker = tail.lastIndexOf("Error:");
        }
        if (marker < 0) {
            marker = Math.max(0, tail.length() - 8_192);
        }
        String excerpt = tail.substring(marker);
        return excerpt.length() > DETAIL_MAX ? excerpt.substring(excerpt.length() - DETAIL_MAX) : excerpt;
    }

    public static String stackTrace(Throwable error)
    {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String lastNonEmptyLine(String value)
    {
        String[] lines = value.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) {
                return lines[i].strip();
            }
        }
        return "";
    }

    private static String truncateReason(String value)
    {
        return value.length() <= 500 ? value : value.substring(0, 500) + "…";
    }
}
