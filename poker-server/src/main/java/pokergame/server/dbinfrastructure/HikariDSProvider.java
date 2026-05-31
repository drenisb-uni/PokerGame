package pokergame.server.dbinfrastructure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class HikariDSProvider {

    private static HikariDataSource dataSource;

    static {
        try {
            Properties props = new Properties();
            // Load credentials dynamically from the application.properties resource file
            try (InputStream input = HikariDSProvider.class.getClassLoader()
                    .getResourceAsStream("application.properties")) {

                if (input != null) {
                    props.load(input);
                } else {
                    System.err.println("[HikariDSProvider] application.properties not found! Using hardcoded fallbacks.");
                }
            }

            // Fallbacks matching your explicit credentials if application.properties keys are absent
            String url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/poker_db?useSSL=false&serverTimezone=UTC");
            String user = props.getProperty("db.username", "root");
            String pass = props.getProperty("db.password", "11X.gjiaDB");

            // Setup Hikari Configuration
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(pass);

            // Explicitly set the driver class name for optimizations
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // --- Performance Tuning Optimizations for Poker Game/MySQL ---
            config.setMaximumPoolSize(10);        // Maximum connections running concurrently
            config.setMinimumIdle(2);             // Minimum idle connections kept alive
            config.setIdleTimeout(30000);         // 30 seconds idle timeout
            config.setConnectionTimeout(20000);   // Wait 20s max for a free connection from pool
            config.setMaxLifetime(1800000);       // 30 minutes connection lifetime max

            // MySQL specific optimizations recommended by HikariCP author
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            // Initialize the pool
            dataSource = new HikariDataSource(config);
            System.out.println("[HikariDSProvider] HikariCP connection pool initialized successfully.");

        } catch (Exception e) {
            System.err.println("[HikariDSProvider] CRITICAL: Failed to initialize HikariCP pool!");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Pulls a fast, pre-warmed connection directly from the pool.
     * Note: Changed from protected to public so Repositories can utilize it safely.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("HikariCP Data Source has not been initialized properly.");
        }
        return dataSource.getConnection();
    }

    /**
     * Call this when your game server explicitly shuts down to clean up connections gracefully
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[HikariDSProvider] Connection pool flushed and closed cleanly.");
        }
    }
}