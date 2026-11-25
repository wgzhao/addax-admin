package com.wgzhao.addax.admin.exception;

/**
 * The target's incremental position (MAX) could not be read for a table that has already been
 * collected at least once.
 *
 * <p>Kept as its own type because it must not be treated like an ordinary job-template failure: the
 * table has a previous template, and that template would read the whole table if it were dispatched.
 * The refresh marks such a table as waiting for update instead of leaving it runnable.</p>
 */
public class IncrementalPositionUnavailableException
    extends RuntimeException
{
    public IncrementalPositionUnavailableException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
