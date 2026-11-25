package com.wgzhao.addax.admin.config.migration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the numbered schema migration chain and records what ran.
 *
 * <p>There is one source of truth for the schema: the chain in {@code db/migration} plus the Java
 * steps that need runtime logic. A fresh database replays the whole chain; an existing database
 * without history is adopted (the baseline snapshot and the reference-data seed are recorded but not
 * re-executed, and the legacy upgrade step below them runs idempotently), so both paths converge on
 * the same schema.</p>
 *
 * <p>Properties of the runner:</p>
 * <ul>
 *   <li>a PostgreSQL advisory transaction lock serializes concurrent instances;</li>
 *   <li>all pending steps and their history rows commit in one transaction, so a failure leaves the
 *       database at the previous version and the next start retries;</li>
 *   <li>an applied step's checksum is verified on every start: a released migration is immutable,
 *       and editing one aborts startup instead of silently diverging from the databases that ran it;</li>
 *   <li>a version present in the history but missing from the resources also aborts startup.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaMigrationRunner
{
    /** Arbitrary but stable key; any instance running the chain contends for the same lock. */
    private static final long MIGRATION_LOCK_KEY = 662012001L;

    private static final String MIGRATION_LOCATION = "classpath*:db/migration/V*.sql";

    private static final Pattern FILE_PATTERN = Pattern.compile("V(\\d{4})__(.+)\\.sql$");

    /**
     * Versions at or below this one are the pre-chain state (the schema snapshot and the seed). An
     * existing database is adopted by recording them as baselined; every later step must be safe to
     * run against an existing database.
     */
    private static final String ADOPTION_BASELINE_VERSION = "0002";

    private static final String CREATE_HISTORY_SQL = """
        create table if not exists public.schema_migration (
            version varchar(32) primary key,
            description varchar(255) not null,
            checksum varchar(64) not null,
            applied_at timestamp with time zone not null default now(),
            execution_ms integer not null,
            baseline boolean not null default false
        )
        """;

    private final JdbcTemplate jdbcTemplate;
    private final List<SchemaMigrationStep> javaSteps;

    @PostConstruct
    public void migrate()
    {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement())
            {
                statement.execute("select pg_advisory_xact_lock(" + MIGRATION_LOCK_KEY + ")");
                boolean fresh = isPublicSchemaEmpty(connection);
                statement.execute(CREATE_HISTORY_SQL);

                Map<String, AppliedMigration> applied = loadApplied(connection);
                List<SchemaMigrationStep> steps;
                try
                {
                    steps = discoverSteps();
                }
                catch (IOException e)
                {
                    throw new IllegalStateException("Failed to read the migration chain", e);
                }
                verifyApplied(steps, applied);

                boolean adoption = applied.isEmpty() && !fresh;
                if (adoption)
                {
                    log.info("Existing schema without migration history; adopting at baseline V{}",
                        ADOPTION_BASELINE_VERSION);
                }

                int executed = 0;
                for (SchemaMigrationStep step : steps)
                {
                    if (applied.containsKey(step.version()))
                    {
                        continue;
                    }
                    boolean baseline = adoption
                        && step.version().compareTo(ADOPTION_BASELINE_VERSION) <= 0;
                    long start = System.currentTimeMillis();
                    if (!baseline)
                    {
                        statement.execute("set search_path = public");
                        step.apply(connection, statement);
                    }
                    int elapsed = (int) (System.currentTimeMillis() - start);
                    recordApplied(connection, step, baseline, elapsed);
                    executed++;
                    log.info("{} migration V{} ({}): {}",
                        baseline ? "Baselined" : "Applied", step.version(), step.description(),
                        elapsed + "ms");
                }
                connection.commit();
                if (executed > 0)
                {
                    log.info("Schema migration chain applied {} step(s)", executed);
                }
                return null;
            }
            catch (SQLException | RuntimeException e)
            {
                connection.rollback();
                throw e;
            }
            finally
            {
                // The bundled baseline dump sets session options (search_path, notices, timeouts);
                // leaking them into a pooled connection would break unrelated statements later.
                try (Statement reset = connection.createStatement())
                {
                    reset.execute("reset all");
                }
                catch (SQLException e)
                {
                    log.debug("Could not reset the session settings after the migration", e);
                }
                connection.setAutoCommit(autoCommit);
            }
        });
    }

    private List<SchemaMigrationStep> discoverSteps() throws IOException
    {
        List<SchemaMigrationStep> steps = new ArrayList<>(javaSteps);
        Set<String> versions = new HashSet<>();
        for (SchemaMigrationStep step : javaSteps)
        {
            if (!versions.add(step.version()))
            {
                throw new IllegalStateException("Duplicate migration version V" + step.version());
            }
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource resource : resolver.getResources(MIGRATION_LOCATION))
        {
            String filename = resource.getFilename();
            Matcher matcher = filename == null ? null : FILE_PATTERN.matcher(filename);
            if (matcher == null || !matcher.matches())
            {
                log.warn("Ignoring migration resource with an unrecognized name: {}", filename);
                continue;
            }
            String version = matcher.group(1);
            if (!versions.add(version))
            {
                throw new IllegalStateException("Duplicate migration version V" + version);
            }
            String sql = read(resource);
            steps.add(new SqlMigrationStep(version, matcher.group(2).replace('_', ' '),
                sha256(sql), sql));
        }
        steps.sort(Comparator.comparing(SchemaMigrationStep::version));
        return steps;
    }

    private void verifyApplied(List<SchemaMigrationStep> steps, Map<String, AppliedMigration> applied)
    {
        Map<String, SchemaMigrationStep> byVersion = new HashMap<>();
        for (SchemaMigrationStep step : steps)
        {
            byVersion.put(step.version(), step);
        }
        for (AppliedMigration migration : applied.values())
        {
            SchemaMigrationStep step = byVersion.get(migration.version());
            if (step == null)
            {
                throw new IllegalStateException("Applied migration V" + migration.version()
                    + " is missing from this build; migrations are append-only");
            }
            if (!step.checksum().equals(migration.checksum()))
            {
                throw new IllegalStateException("Migration V" + migration.version()
                    + " changed after it was applied (checksum mismatch); released migrations are "
                    + "immutable, add a new version instead");
            }
        }
    }

    private Map<String, AppliedMigration> loadApplied(Connection connection) throws SQLException
    {
        Map<String, AppliedMigration> applied = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
            "select version, checksum from public.schema_migration");
            ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
            {
                applied.put(rs.getString("version"),
                    new AppliedMigration(rs.getString("version"), rs.getString("checksum")));
            }
        }
        return applied;
    }

    private void recordApplied(Connection connection, SchemaMigrationStep step, boolean baseline,
        int elapsedMs) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement("""
            insert into public.schema_migration
                (version, description, checksum, execution_ms, baseline)
            values (?, ?, ?, ?, ?)
            """))
        {
            ps.setString(1, step.version());
            ps.setString(2, step.description());
            ps.setString(3, step.checksum());
            ps.setInt(4, elapsedMs);
            ps.setBoolean(5, baseline);
            ps.executeUpdate();
        }
    }

    private boolean isPublicSchemaEmpty(Connection connection) throws SQLException
    {
        try (PreparedStatement ps = connection.prepareStatement(
            "select count(*) from information_schema.tables where table_schema = 'public'");
            ResultSet rs = ps.executeQuery())
        {
            return rs.next() && rs.getLong(1) == 0;
        }
    }

    private String read(Resource resource) throws IOException
    {
        try (InputStream in = resource.getInputStream())
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record AppliedMigration(String version, String checksum)
    {
    }
}
