package com.wgzhao.addax.admin.config.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One application-recorded schema change, either a versioned SQL resource or a Java step.
 *
 * <p>Versions are global and append-only: {@code 0001}, {@code 0002}, … A released step is never
 * edited; its checksum is stored when it is applied and a later change triggers a startup failure
 * instead of silently diverging from the databases that already ran it. Behavior that cannot be
 * expressed as SQL (conditional view drops, JSON rewrites, runtime policy probes) stays a Java step
 * but still lives in the same ordered chain.</p>
 */
public interface SchemaMigrationStep
{
    /** Four-digit, zero-padded, globally increasing version. */
    String version();

    String description();

    /**
     * SHA-256 of the SQL text, or a stable marker for a Java step. Stored on apply and verified on
     * every later startup.
     */
    String checksum();

    /**
     * Applies the step to the migration connection. The runner has already set the search path and
     * holds the advisory lock; it also runs each step inside the migration transaction.
     */
    void apply(Connection connection, Statement statement) throws SQLException;
}
