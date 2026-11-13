package com.wgzhao.addax.admin.common;

import java.util.Locale;

/**
 * A coarse, display-only classification of a failure message.
 *
 * <p>The category never replaces the original message and is never used to suppress or auto-resolve
 * a failure. It exists so an operator can filter a long history by the kind of problem and so the UI
 * can offer a next step for common cases (a missing source table, an expired connection).</p>
 */
public final class FailureCategory
{
    public static final String OOM = "OOM";
    public static final String NETWORK = "NETWORK";
    public static final String DISK = "DISK";
    public static final String AUTH = "AUTH";
    public static final String TABLE_NOT_FOUND = "TABLE_NOT_FOUND";
    public static final String CONTRACT = "CONTRACT";
    public static final String DATA = "DATA";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String PROCESS = "PROCESS";
    public static final String UNKNOWN = "UNKNOWN";

    private FailureCategory() {}

    public static String classify(String summary)
    {
        if (summary == null || summary.isBlank()) {
            return UNKNOWN;
        }
        String text = summary.toLowerCase(Locale.ROOT);
        // Order matters: the most specific and most actionable condition wins.
        if (containsAny(text, "outofmemoryerror", "out of memory", "exit code: 137",
            "exit code 137", "oomkilled", "killed process"))
        {
            return OOM;
        }
        if (containsAny(text, "no space left", "disk full", "quota exceeded", "磁盘空间"))
        {
            return DISK;
        }
        if (containsAny(text, "connection refused", "connection reset", "no route to host",
            "unknownhost", "communications link failure", "could not connect",
            "connection is not available", "broken pipe", "连接被拒绝", "无法连接"))
        {
            return NETWORK;
        }
        if (containsAny(text, "accesscontrol", "permission denied", "authentication failed",
            "invalid password", "access denied", "not authorized", "unauthorized",
            "认证失败", "密码错误", "无权限"))
        {
            return AUTH;
        }
        if (containsAny(text, "not found by given table id", "table or view not found",
            "unknown table", "no such table", "table does not exist", "doesn't exist",
            "表不存在", "object not found", "relation does not exist"))
        {
            return TABLE_NOT_FOUND;
        }
        if (containsAny(text, "target contract", "契约校验", "契约阻断"))
        {
            return CONTRACT;
        }
        if (containsAny(text, "timed out", "timeout", "超时"))
        {
            return TIMEOUT;
        }
        if (containsAny(text, "duplicate key", "constraint violation", "numberformatexception",
            "unknown column", "conversion", "脏数据", "dataxexception", "illegalargument",
            "类型转换"))
        {
            return DATA;
        }
        if (containsAny(text, "exit code", "exit status", "非零退出", "non-zero exit"))
        {
            return PROCESS;
        }
        return UNKNOWN;
    }

    private static boolean containsAny(String text, String... needles)
    {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
