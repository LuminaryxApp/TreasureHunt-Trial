package com.luminary.treasurehunt.database;

import com.luminary.treasurehunt.TreasureHunt;
import com.luminary.treasurehunt.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Handles all database operations using HikariCP for connection pooling.
 * This is the backbone of multi-server support - all claim checks go through here.
 */
public class DatabaseManager {

    private final TreasureHunt plugin;
    private final ConfigManager config;
    private HikariDataSource dataSource;

    // Table names (configurable for shared databases)
    private String treasuresTable;
    private String completionsTable;

    public DatabaseManager(TreasureHunt plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    /**
     * Initialize the connection pool and create tables if needed.
     * Call this in onEnable().
     */
    public boolean initialize() {
        treasuresTable = config.getTreasuresTable();
        completionsTable = config.getCompletionsTable();

        try {
            setupHikari();
            createTables();
            plugin.getLogger().info("Database connection established!");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database!", e);
            return false;
        }
    }

    private void setupHikari() {
        HikariConfig hikariConfig = new HikariConfig();

        // Build the JDBC URL
        String host = config.getDatabaseHost();
        int port = config.getDatabasePort();
        String database = config.getDatabaseName();
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true";

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getDatabaseUsername());
        hikariConfig.setPassword(config.getDatabasePassword());

        // Pool settings from config
        hikariConfig.setMaximumPoolSize(config.getPoolMaxSize());
        hikariConfig.setMinimumIdle(config.getPoolMinIdle());
        hikariConfig.setConnectionTimeout(config.getPoolConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getPoolIdleTimeout());
        hikariConfig.setMaxLifetime(config.getPoolMaxLifetime());

        // Some sensible defaults
        hikariConfig.setPoolName("TreasureHunt-Pool");
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * Create the database tables if they don't exist.
     */
    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            // Treasures table - stores all treasure definitions
            stmt.execute("CREATE TABLE IF NOT EXISTS " + treasuresTable + " ("
                    + "id VARCHAR(64) PRIMARY KEY, "
                    + "world VARCHAR(64) NOT NULL, "
                    + "x INT NOT NULL, "
                    + "y INT NOT NULL, "
                    + "z INT NOT NULL, "
                    + "command VARCHAR(512) NOT NULL, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")");

            // Completions table - tracks which players found which treasures
            stmt.execute("CREATE TABLE IF NOT EXISTS " + completionsTable + " ("
                    + "treasure_id VARCHAR(64) NOT NULL, "
                    + "player_uuid VARCHAR(36) NOT NULL, "
                    + "completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (treasure_id, player_uuid), "
                    + "FOREIGN KEY (treasure_id) REFERENCES " + treasuresTable + "(id) ON DELETE CASCADE"
                    + ")");
        }
    }

    /**
     * Get a connection from the pool.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized!");
        }
        return dataSource.getConnection();
    }

    /**
     * Clean up the connection pool when the plugin disables.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    // ==========================================
    //         TREASURE CRUD OPERATIONS
    // ==========================================

    /**
     * Save a new treasure to the database.
     */
    public CompletableFuture<Boolean> saveTreasure(Treasure treasure) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + treasuresTable
                    + " (id, world, x, y, z, command) VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, treasure.getId());
                ps.setString(2, treasure.getWorldName());
                ps.setInt(3, treasure.getX());
                ps.setInt(4, treasure.getY());
                ps.setInt(5, treasure.getZ());
                ps.setString(6, treasure.getCommand());

                ps.executeUpdate();
                return true;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save treasure: " + treasure.getId(), e);
                return false;
            }
        });
    }

    /**
     * Delete a treasure from the database.
     * Completions are deleted automatically thanks to ON DELETE CASCADE.
     */
    public CompletableFuture<Boolean> deleteTreasure(String id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + treasuresTable + " WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, id);
                int affected = ps.executeUpdate();
                return affected > 0;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete treasure: " + id, e);
                return false;
            }
        });
    }

    /**
     * Check if a treasure with the given ID exists.
     */
    public boolean treasureExists(String id) {
        String sql = "SELECT 1 FROM " + treasuresTable + " WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check treasure existence: " + id, e);
            return false;
        }
    }

    /**
     * Load all treasures from the database.
     * Called on startup to populate the in-memory cache.
     */
    public List<Treasure> loadAllTreasures() {
        List<Treasure> treasures = new ArrayList<>();
        String sql = "SELECT * FROM " + treasuresTable;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Treasure t = new Treasure(
                        rs.getString("id"),
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("command"),
                        rs.getTimestamp("created_at")
                );
                treasures.add(t);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load treasures from database!", e);
        }

        return treasures;
    }

    // ==========================================
    //         COMPLETION OPERATIONS
    // ==========================================

    /**
     * Check if a player has already claimed a treasure.
     * This hits the database every time for multi-server support.
     */
    public CompletableFuture<Boolean> hasCompleted(UUID playerUuid, String treasureId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + completionsTable
                    + " WHERE treasure_id = ? AND player_uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, treasureId);
                ps.setString(2, playerUuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check completion status", e);
                // Better safe than sorry - assume they haven't completed it
                return false;
            }
        });
    }

    /**
     * Synchronous version of hasCompleted for when you really need the result immediately.
     */
    public boolean hasCompletedSync(UUID playerUuid, String treasureId) {
        String sql = "SELECT 1 FROM " + completionsTable
                + " WHERE treasure_id = ? AND player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, treasureId);
            ps.setString(2, playerUuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check completion status", e);
            return false;
        }
    }

    /**
     * Mark a treasure as completed by a player.
     */
    public CompletableFuture<Boolean> markCompleted(UUID playerUuid, String treasureId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + completionsTable
                    + " (treasure_id, player_uuid) VALUES (?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, treasureId);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
                return true;

            } catch (SQLException e) {
                // Might fail if they already completed it (duplicate key)
                // That's fine, we just return false
                return false;
            }
        });
    }

    /**
     * Get all players who have completed a specific treasure.
     * Returns a list of CompletionEntry with UUID and timestamp.
     */
    public List<CompletionEntry> getCompletions(String treasureId) {
        List<CompletionEntry> completions = new ArrayList<>();
        String sql = "SELECT player_uuid, completed_at FROM " + completionsTable
                + " WHERE treasure_id = ? ORDER BY completed_at ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, treasureId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    Timestamp completedAt = rs.getTimestamp("completed_at");
                    completions.add(new CompletionEntry(uuid, completedAt));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get completions for: " + treasureId, e);
        }

        return completions;
    }

    /**
     * Get the total count of completions for a treasure.
     */
    public int getCompletionCount(String treasureId) {
        String sql = "SELECT COUNT(*) FROM " + completionsTable + " WHERE treasure_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, treasureId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to count completions", e);
        }

        return 0;
    }

    /**
     * Simple data class for holding completion info.
     */
    public static class CompletionEntry {
        private final UUID playerUuid;
        private final Timestamp completedAt;

        public CompletionEntry(UUID playerUuid, Timestamp completedAt) {
            this.playerUuid = playerUuid;
            this.completedAt = completedAt;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public Timestamp getCompletedAt() {
            return completedAt;
        }
    }
}
