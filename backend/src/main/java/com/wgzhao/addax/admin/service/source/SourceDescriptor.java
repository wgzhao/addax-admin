package com.wgzhao.addax.admin.service.source;

import java.util.List;

/**
 * UI-facing description of a registered source engine.
 *
 * @param readerPluginInstalled null when the Addax home cannot be inspected
 */
public record SourceDescriptor(
    String type,
    String label,
    String description,
    String connectionMode,
    List<String> fields,
    SourceCapabilities capabilities,
    String defaultFilter,
    Boolean readerPluginInstalled
)
{
}
