package com.wgzhao.addax.admin.common;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Loads JDBC drivers from {@code ${APP_HOME}/drivers} in an isolated classloader.
 *
 * <p>The driver directory used to be added to the application classpath through
 * {@code -Dloader.path}. Any vendor "standalone" jar placed there (HDP/Cloudera Hive JDBC is the
 * common case) then shared the application classpath and could break startup with its bundled
 * SLF4J provider or Tomcat classes before an administrator could even open the UI.</p>
 *
 * <p>This registry keeps the same user workflow — drop a JDBC 4 driver jar into {@code drivers/}
 * and restart — but registers a single {@link Driver} shim with {@link DriverManager}. The shim
 * resolves the URL against drivers loaded in a child classloader, lazily and per driver, so a
 * broken or incompatible jar only fails the connections that need it. Normal usage through
 * {@link DriverManager} in {@code DbUtil} is unchanged.</p>
 */
@Component
@Slf4j
public class JdbcDriverRegistry
{
    private static final String DRIVER_SERVICE = "META-INF/services/java.sql.Driver";
    private static final String DRIVERS_DIR_PROPERTY = "addax.drivers.dir";
    private static final String DRIVERS_DIR_ENV = "ADDAX_DRIVERS_DIR";

    private final Map<String, Optional<Driver>> driversByClass = new ConcurrentHashMap<>();
    private final Map<String, DriverHandle> externalDrivers = new ConcurrentHashMap<>();
    private final List<DriverCandidate> candidates = new ArrayList<>();

    private URLClassLoader driversClassLoader;
    private Driver shim;

    @PostConstruct
    public void init()
    {
        Path dir = driversDir();
        if (dir == null || !Files.isDirectory(dir))
        {
            log.info("JDBC driver directory not found: {}", dir);
            return;
        }
        List<Path> jars;
        try (Stream<Path> stream = Files.list(dir))
        {
            jars = stream
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .sorted()
                .toList();
        }
        catch (Exception e)
        {
            log.warn("Failed to list JDBC driver jars in {}: {}", dir, e.getMessage());
            return;
        }
        if (jars.isEmpty())
        {
            log.info("No JDBC driver jars found in {}", dir);
            return;
        }
        try
        {
            URL[] urls = new URL[jars.size()];
            for (int i = 0; i < jars.size(); i++)
            {
                urls[i] = jars.get(i).toUri().toURL();
            }
            driversClassLoader = new URLClassLoader(urls, getClass().getClassLoader());
        }
        catch (Exception e)
        {
            log.warn("Failed to create the isolated JDBC driver classloader for {}: {}", dir, e.getMessage());
            return;
        }
        for (Path jar : jars)
        {
            for (String className : readDriverClasses(jar))
            {
                candidates.add(new DriverCandidate(jar.getFileName().toString(), className));
            }
        }
        if (candidates.isEmpty())
        {
            log.warn("No JDBC 4 driver service entries found in {}; check that each jar has "
                + "META-INF/services/java.sql.Driver", dir);
            return;
        }
        shim = new RegistryDriverShim(this);
        try
        {
            DriverManager.registerDriver(shim);
        }
        catch (SQLException e)
        {
            log.error("Failed to register the isolated JDBC driver shim: {}", e.getMessage());
            shim = null;
            return;
        }
        log.info("JDBC drivers will be loaded in an isolated classloader from {}: {}", dir,
            candidates.stream().map(DriverCandidate::className).toList());
    }

    /**
     * Whether a jar with this file name is already available in the shared driver directory. Used by
     * the migration to avoid pinning a target to an old absolute path after the jar was moved here.
     */
    public boolean hasSharedDriver(String fileName)
    {
        if (fileName == null || fileName.isBlank())
        {
            return false;
        }
        Path dir = driversDir();
        return dir != null && Files.isRegularFile(dir.resolve(Path.of(fileName).getFileName()));
    }

    public boolean supports(String url)
    {
        return url != null && resolve(url) != null;
    }

    public Connection connect(String url, Properties info) throws SQLException
    {
        Driver driver = resolve(url);
        return driver == null ? null : driver.connect(url, info);
    }

    /**
     * Load a driver jar outside the shared driver directory (the optional per-target
     * {@code driverPath}). The jar is self-contained by design; it cannot see other jars in
     * {@code drivers/} and is loaded in its own child classloader.
     */
    public Driver loadExternalDriver(String path, String className) throws SQLException
    {
        String key = path + "\u0000" + className;
        DriverHandle cached = externalDrivers.get(key);
        if (cached != null)
        {
            return cached.driver();
        }
        synchronized (externalDrivers)
        {
            cached = externalDrivers.get(key);
            if (cached != null)
            {
                return cached.driver();
            }
            File jar = new File(path);
            if (!jar.isFile())
            {
                throw new SQLException("Hive 驱动 jar 不存在: " + path);
            }
            try
            {
                URLClassLoader loader = new URLClassLoader(new URL[] {jar.toURI().toURL()},
                    getClass().getClassLoader());
                Class<?> driverClass = Class.forName(className, true, loader);
                Object instance = driverClass.getDeclaredConstructor().newInstance();
                if (!(instance instanceof Driver driver))
                {
                    loader.close();
                    throw new SQLException("配置的驱动类不是 java.sql.Driver: " + className);
                }
                externalDrivers.put(key, new DriverHandle(driver, loader));
                log.info("Loaded external JDBC driver {} from {}", className, path);
                return driver;
            }
            catch (SQLException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new SQLException("加载驱动失败 (" + path + "): " + e.getMessage(), e);
            }
        }
    }

    private Driver resolve(String url)
    {
        for (DriverCandidate candidate : candidates)
        {
            Optional<Driver> driver = driversByClass.computeIfAbsent(candidate.className(),
                name -> loadDriver(candidate));
            if (driver.isEmpty())
            {
                continue;
            }
            try
            {
                if (driver.get().acceptsURL(url))
                {
                    return driver.get();
                }
            }
            catch (SQLException e)
            {
                log.debug("JDBC driver {} rejected url {}: {}", candidate.className(), url, e.getMessage());
            }
        }
        return null;
    }

    private Optional<Driver> loadDriver(DriverCandidate candidate)
    {
        try
        {
            Class<?> driverClass = Class.forName(candidate.className(), true, driversClassLoader);
            Object instance = driverClass.getDeclaredConstructor().newInstance();
            if (instance instanceof Driver driver)
            {
                log.info("Loaded JDBC driver {} from {}", candidate.className(), candidate.jar());
                return Optional.of(driver);
            }
            log.warn("Class {} from {} is not a java.sql.Driver", candidate.className(), candidate.jar());
        }
        catch (Throwable e)
        {
            // A broken driver jar must never take the application down. It only breaks the URLs
            // that would have used this driver.
            log.warn("Failed to load JDBC driver {} from {}: {}",
                candidate.className(), candidate.jar(), e.toString());
        }
        return Optional.empty();
    }

    private List<String> readDriverClasses(Path jar)
    {
        try (JarFile jarFile = new JarFile(jar.toFile()))
        {
            JarEntry entry = jarFile.getJarEntry(DRIVER_SERVICE);
            if (entry == null)
            {
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8)))
            {
                return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
            }
        }
        catch (Exception e)
        {
            log.warn("Skipping unreadable JDBC driver jar {}: {}", jar.getFileName(), e.getMessage());
            return List.of();
        }
    }

    private Path driversDir()
    {
        String configured = System.getProperty(DRIVERS_DIR_PROPERTY);
        if (configured == null || configured.isBlank())
        {
            configured = System.getenv(DRIVERS_DIR_ENV);
        }
        if (configured == null || configured.isBlank())
        {
            String appHome = System.getProperty("app.home");
            if (appHome != null && !appHome.isBlank())
            {
                configured = Path.of(appHome, "drivers").toString();
            }
        }
        return configured == null || configured.isBlank() ? null : Path.of(configured);
    }

    @PreDestroy
    public void close()
    {
        if (shim != null)
        {
            try
            {
                DriverManager.deregisterDriver(shim);
            }
            catch (SQLException e)
            {
                log.warn("Failed to deregister the isolated JDBC driver shim: {}", e.getMessage());
            }
            shim = null;
        }
        externalDrivers.values().forEach(handle -> closeQuietly(handle.loader()));
        externalDrivers.clear();
        closeQuietly(driversClassLoader);
        driversClassLoader = null;
        driversByClass.clear();
        candidates.clear();
    }

    private void closeQuietly(URLClassLoader loader)
    {
        try
        {
            if (loader != null)
            {
                loader.close();
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to close JDBC driver classloader: {}", e.getMessage());
        }
    }

    private record DriverCandidate(String jar, String className)
    {
    }

    private record DriverHandle(Driver driver, URLClassLoader loader)
    {
    }

    private static final class RegistryDriverShim
        implements Driver
    {
        private final JdbcDriverRegistry registry;

        private RegistryDriverShim(JdbcDriverRegistry registry)
        {
            this.registry = registry;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException
        {
            return registry.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url)
        {
            return registry.supports(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
        {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion()
        {
            return 1;
        }

        @Override
        public int getMinorVersion()
        {
            return 0;
        }

        @Override
        public boolean jdbcCompliant()
        {
            return false;
        }

        @Override
        public Logger getParentLogger()
            throws SQLFeatureNotSupportedException
        {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
