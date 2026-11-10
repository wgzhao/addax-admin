package com.wgzhao.addax.admin.alert;

public enum AlertSeverity
{
    P1(1), P2(2), P3(3);

    private final int rank;

    AlertSeverity(int rank)
    {
        this.rank = rank;
    }

    /**
     * Lower rank means more severe; the router keeps the most severe rule level
     * when several rules match the same event.
     */
    public static AlertSeverity mostSevere(AlertSeverity left, AlertSeverity right)
    {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.rank <= right.rank ? left : right;
    }

    public static AlertSeverity parse(String value, AlertSeverity fallback)
    {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
