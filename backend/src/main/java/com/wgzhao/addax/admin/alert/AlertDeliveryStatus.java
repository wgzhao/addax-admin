package com.wgzhao.addax.admin.alert;

public enum AlertDeliveryStatus
{
    PENDING, SENDING, SENT, FAILED, DEAD, SKIPPED_NO_CHANNEL, SILENCED;

    public static AlertDeliveryStatus parse(String value)
    {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
