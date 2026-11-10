package com.wgzhao.addax.admin.alert;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AlertDeliveryResult(
    boolean success,
    Integer responseCode,
    String responseBody,
    long latencyMs,
    String error
)
{
    public static AlertDeliveryResult ok(int responseCode, String responseBody, long latencyMs)
    {
        return new AlertDeliveryResult(true, responseCode, responseBody, latencyMs, null);
    }

    public static AlertDeliveryResult failed(Integer responseCode, String responseBody, long latencyMs, String error)
    {
        return new AlertDeliveryResult(false, responseCode, responseBody, latencyMs, error);
    }
}
