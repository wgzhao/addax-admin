package com.wgzhao.addax.admin.alert;

import tools.jackson.databind.JsonNode;

public interface AlertChannelProvider
{
    AlertChannelType type();

    /**
     * Reject a configuration the provider cannot use. Validation runs both on
     * save and again before a test/send, because DNS and endpoints can change.
     */
    void validate(JsonNode config);

    AlertDeliveryResult send(JsonNode config, AlertMessage message);
}
