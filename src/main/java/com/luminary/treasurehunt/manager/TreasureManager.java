package com.luminary.treasurehunt.manager;

import com.luminary.treasurehunt.TreasureHunt;
import com.luminary.treasurehunt.config.ConfigManager;
import com.luminary.treasurehunt.database.DatabaseManager;
import com.luminary.treasurehunt.database.Treasure;
import com.luminary.treasurehunt.util.MessageUtil;
import com.luminary.treasurehunt.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages treasures in memory and coordinates with the database.
 * Keeps a local cache for fast location lookups, but always checks
 * the database for claim status (multi-server support).
 */
public class TreasureManager {

    private final TreasureHunt plugin;
    private final ConfigManager config;
    private final DatabaseManager database;

    // In-memory cache for fast lookups by location
    // Key: "world:x:y:z" -> Treasure
    private final Map<String, Treasure> locationCache = new ConcurrentHashMap<>();

    // Also keep them indexed by ID for commands
    private final Map<String, Treasure> idCache = new ConcurrentHashMap<>();

    // Sync task for multi-server support
    private BukkitTask syncTask;

    public TreasureManager(TreasureHunt plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.database = plugin.getDatabaseManager();
    }

    /**
     * Start the sync task that periodically reloads treasures from the database.
     * This keeps all servers in sync without requiring restarts.
     */
    public void startSyncTask() {
        int interval = config.getSyncInterval();
        if (interval <= 0) {
            plugin.getLogger().info("Treasure sync disabled (sync-interval: 0)");
            return;
        }

        long ticks = interval * 20L; // Convert seconds to ticks
        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            syncFromDatabase();
        }, ticks, ticks);

        plugin.getLogger().info("Treasure sync enabled (every " + interval + " seconds)");
    }

    /**
     * Stop the sync task.
     */
    public void stopSyncTask() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
    }

    /**
     * Sync treasures from database. Adds new ones, removes deleted ones.
     * Runs async to avoid blocking the main thread.
     */
    private void syncFromDatabase() {
        List<Treasure> dbTreasures = database.loadAllTreasures();
        Set<String> dbIds = new HashSet<>();

        for (Treasure t : dbTreasures) {
            dbIds.add(t.getId().toLowerCase());
            // Add if not already cached
            if (!idCache.containsKey(t.getId().toLowerCase())) {
                cacheLocally(t);
                plugin.getLogger().info("Synced new treasure from database: " + t.getId());
            }
        }

        // Remove treasures that no longer exist in DB
        List<String> toRemove = new ArrayList<>();
        for (String id : idCache.keySet()) {
            if (!dbIds.contains(id)) {
                toRemove.add(id);
            }
        }

        for (String id : toRemove) {
            Treasure t = idCache.get(id);
            if (t != null) {
                removeFromCache(t);
                plugin.getLogger().info("Removed deleted treasure from cache: " + id);
            }
        }
    }

    /**
     * Load all treasures from database into memory.
     * Called on startup.
     */
    public void loadTreasures() {
        locationCache.clear();
        idCache.clear();

        List<Treasure> treasures = database.loadAllTreasures();
        for (Treasure t : treasures) {
            cacheLocally(t);
        }

        plugin.getLogger().info("Loaded " + treasures.size() + " treasures from database.");
    }

    private void cacheLocally(Treasure treasure) {
        String locKey = makeLocationKey(treasure.getWorldName(), treasure.getX(), treasure.getY(), treasure.getZ());
        locationCache.put(locKey, treasure);
        idCache.put(treasure.getId().toLowerCase(), treasure);
    }

    private void removeFromCache(Treasure treasure) {
        String locKey = makeLocationKey(treasure.getWorldName(), treasure.getX(), treasure.getY(), treasure.getZ());
        locationCache.remove(locKey);
        idCache.remove(treasure.getId().toLowerCase());
    }

    private String makeLocationKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private String makeLocationKey(Location loc) {
        return makeLocationKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // ==========================================
    //         QUERIES
    // ==========================================

    /**
     * Get a treasure at a specific location (fast, uses cache).
     */
    public Treasure getTreasureAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return locationCache.get(makeLocationKey(location));
    }

    /**
     * Get a treasure by ID (case-insensitive).
     */
    public Treasure getTreasureById(String id) {
        return idCache.get(id.toLowerCase());
    }

    /**
     * Check if a treasure ID already exists.
     */
    public boolean treasureExists(String id) {
        return idCache.containsKey(id.toLowerCase());
    }

    /**
     * Get all treasures (copy of values).
     */
    public Collection<Treasure> getAllTreasures() {
        return new ArrayList<>(idCache.values());
    }

    /**
     * Get total count of treasures.
     */
    public int getTreasureCount() {
        return idCache.size();
    }

    // ==========================================
    //         CREATE / DELETE
    // ==========================================

    /**
     * Create a new treasure. Saves to database and adds to cache.
     */
    public CompletableFuture<Boolean> createTreasure(String id, Location location, String command) {
        Treasure treasure = new Treasure(id, location, command);

        return database.saveTreasure(treasure).thenApply(success -> {
            if (success) {
                cacheLocally(treasure);

                // Spawn some particles at the location if enabled
                if (config.isParticleEnabled("on-create")) {
                    Location loc = treasure.getLocation();
                    if (loc != null && loc.getWorld() != null) {
                        Particle particle = config.getParticleType("on-create");
                        int count = config.getParticleCount("on-create");
                        loc.getWorld().spawnParticle(particle, loc.add(0.5, 0.5, 0.5), count);
                    }
                }
            }
            return success;
        });
    }

    /**
     * Delete a treasure. Removes from database and cache.
     */
    public CompletableFuture<Boolean> deleteTreasure(String id) {
        Treasure treasure = getTreasureById(id);
        if (treasure == null) {
            return CompletableFuture.completedFuture(false);
        }

        return database.deleteTreasure(treasure.getId()).thenApply(success -> {
            if (success) {
                removeFromCache(treasure);
            }
            return success;
        });
    }

    // ==========================================
    //         CLAIMING
    // ==========================================

    /**
     * Attempt to claim a treasure for a player.
     * Returns a result enum so the caller knows what happened.
     */
    public CompletableFuture<ClaimResult> claimTreasure(Player player, Treasure treasure) {
        UUID uuid = player.getUniqueId();
        String treasureId = treasure.getId();

        // First check if they already claimed it (hits database for multi-server support)
        return database.hasCompleted(uuid, treasureId).thenCompose(alreadyClaimed -> {
            if (alreadyClaimed && !player.hasPermission("treasurehunt.bypass")) {
                return CompletableFuture.completedFuture(ClaimResult.ALREADY_CLAIMED);
            }

            // Mark as completed
            return database.markCompleted(uuid, treasureId).thenApply(success -> {
                if (!success) {
                    return ClaimResult.ERROR;
                }

                // Run the reward command on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    executeCommand(player, treasure);
                    playClaimEffects(player, treasure);
                });

                // Log it if enabled
                if (config.isLogClaims()) {
                    plugin.getLogger().info(player.getName() + " claimed treasure: " + treasureId);
                }

                return ClaimResult.SUCCESS;
            });
        });
    }

    /**
     * Execute the treasure's command, replacing %player% with the player's name.
     */
    private void executeCommand(Player player, Treasure treasure) {
        String command = treasure.getCommand().replace("%player%", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    /**
     * Play the claim sound and particles.
     */
    private void playClaimEffects(Player player, Treasure treasure) {
        // Sound
        SoundUtil.playSound(player, config.getSoundSection("treasure-claimed"), plugin.getLogger());

        // Particles
        if (config.isParticleEnabled("on-claim")) {
            Location loc = treasure.getLocation();
            if (loc != null && loc.getWorld() != null) {
                Particle particle = config.getParticleType("on-claim");
                int count = config.getParticleCount("on-claim");
                loc.getWorld().spawnParticle(particle, loc.add(0.5, 1, 0.5), count);
            }
        }
    }

    /**
     * Play the "already claimed" sound for a player.
     */
    public void playAlreadyClaimedSound(Player player) {
        SoundUtil.playSound(player, config.getSoundSection("already-claimed"), plugin.getLogger());
    }

    public enum ClaimResult {
        SUCCESS,
        ALREADY_CLAIMED,
        ERROR
    }
}
