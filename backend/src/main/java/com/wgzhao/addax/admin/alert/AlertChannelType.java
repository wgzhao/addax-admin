package com.wgzhao.addax.admin.alert;

/**
 * Delivery channels with an implemented provider. SMS is deliberately absent
 * until its provider exists, so the UI cannot offer a channel that would
 * silently drop every message.
 */
public enum AlertChannelType
{
    WECOM("企业微信", 20),
    DINGTALK("钉钉", 20),
    FEISHU("飞书", 100),
    WEBHOOK("Webhook", 60),
    EMAIL("邮件", 30);

    private final String label;
    private final int defaultRateLimitPerMinute;

    AlertChannelType(String label, int defaultRateLimitPerMinute)
    {
        this.label = label;
        this.defaultRateLimitPerMinute = defaultRateLimitPerMinute;
    }

    public String label()
    {
        return label;
    }

    public int defaultRateLimitPerMinute()
    {
        return defaultRateLimitPerMinute;
    }
}
