package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.Constants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * One-time credentials for the anonymous {@code /log/job-report} endpoint.
 *
 * <p>Addax reports over its own HTTP client and cannot carry a JWT, so the report is authenticated
 * by a per-attempt token embedded in the {@code jobName} JVM property. Production attempts get the
 * token from {@code etl_execution.report_token}; smoke checks register a short-lived token in Redis
 * because they do not create an execution row.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobReportTokenService
{
    private static final Duration SMOKE_TOKEN_TTL = Duration.ofMinutes(10);
    private static final Duration CONSUMED_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    public static String newToken()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Constant-time comparison so a failed report does not leak the expected prefix. */
    public static boolean matches(String expected, String provided)
    {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8));
    }

    public String issueSmokeToken(long tid)
    {
        String token = newToken();
        redisTemplate.opsForValue().set(Constants.JOB_REPORT_SMOKE_TOKEN_PREFIX + token,
            String.valueOf(tid), SMOKE_TOKEN_TTL);
        return token;
    }

    public void revokeSmokeToken(String token)
    {
        if (token == null) {
            return;
        }
        try {
            redisTemplate.delete(Constants.JOB_REPORT_SMOKE_TOKEN_PREFIX + token);
        }
        catch (Exception e) {
            // Cleanup is best effort; the ten-minute TTL bounds an unreachable key.
            log.warn("Failed to revoke smoke report token: {}", e.getMessage());
        }
    }

    /**
     * Consume a smoke token registered by the validation/health-check service. The tid is part of
     * the stored value so one token cannot be replayed against a different reserved tid.
     */
    public boolean consumeSmokeToken(long tid, String token)
    {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = Constants.JOB_REPORT_SMOKE_TOKEN_PREFIX + token;
        String registered = redisTemplate.opsForValue().get(key);
        if (!String.valueOf(tid).equals(registered)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * Claim the one report this execution is allowed to submit. Returns false when a report was
     * already accepted, which blocks replaying a captured token against historical statistics.
     */
    public boolean consumeExecutionReport(long executionId)
    {
        String key = Constants.JOB_REPORT_CONSUMED_KEY_PREFIX + executionId;
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", CONSUMED_TTL));
    }
}
