package com.wgzhao.addax.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TargetContractReport(
    String status,
    LocalDateTime checkedAt,
    String fingerprint,
    TargetTableMetadata target,
    List<Finding> findings
)
{
    public static final String READY = "READY";
    public static final String WARNING = "WARNING";
    public static final String ERROR = "ERROR";

    public boolean blocked()
    {
        return ERROR.equals(status);
    }

    public String summary()
    {
        if (findings.isEmpty())
        {
            return "目标表结构校验通过";
        }
        return findings.stream()
            .map(f -> f.severity() + " " + f.message())
            .limit(5)
            .reduce((a, b) -> a + "; " + b)
            .orElse(status);
    }

    public record Finding(
        String severity,
        String code,
        String column,
        String expected,
        String actual,
        String message
    )
    {
        public static Finding error(String code, String column, String expected, String actual, String message)
        {
            return new Finding(ERROR, code, column, expected, actual, message);
        }

        public static Finding warning(String code, String column, String expected, String actual, String message)
        {
            return new Finding(WARNING, code, column, expected, actual, message);
        }
    }
}
