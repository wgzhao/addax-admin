package com.wgzhao.addax.admin.service.source;

/**
 * A source connection/metadata failure. {@code unavailable} distinguishes "could not connect" from
 * "connected but the metadata query failed", which the column refresh pipeline reports differently.
 */
public class SourceAccessException extends RuntimeException
{
    private final boolean unavailable;

    public SourceAccessException(String message, boolean unavailable)
    {
        super(message);
        this.unavailable = unavailable;
    }

    public SourceAccessException(String message, boolean unavailable, Throwable cause)
    {
        super(message, cause);
        this.unavailable = unavailable;
    }

    public boolean isUnavailable()
    {
        return unavailable;
    }
}
