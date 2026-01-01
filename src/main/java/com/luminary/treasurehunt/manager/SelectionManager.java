package com.luminary.treasurehunt.manager;

import com.luminary.treasurehunt.TreasureHunt;
import com.luminary.treasurehunt.config.ConfigManager;
import com.luminary.treasurehunt.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the "selection mode" for treasure creation.
 * When an admin runs /treasure create, they enter selection mode
 * and then click a block to choose the treasure location.
 */
public class SelectionManager {

    private final TreasureHunt plugin;
    private final ConfigManager config;

    // Players currently selecting a block
    private final Map<UUID, PendingTreasure> pendingSelections = new ConcurrentHashMap<>();

    // Cleanup task for expired selections
    private BukkitTask cleanupTask;

    public SelectionManager(TreasureHunt plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        startCleanupTask();
    }

    /**
     * Put a player into selection mode.
     * They need to click a block to complete the treasure creation.
     */
    public void startSelection(Player player, String treasureId, String command) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing selection
        if (pendingSelections.containsKey(uuid)) {
            pendingSelections.remove(uuid);
        }

        long expiresAt = System.currentTimeMillis() + (config.getSelectionTimeout() * 1000L);
        PendingTreasure pending = new PendingTreasure(treasureId, command, expiresAt);
        pendingSelections.put(uuid, pending);

        // Play the selection start sound
        SoundUtil.playSound(player, config.getSoundSection("selection-start"), plugin.getLogger());
    }

    /**
     * Check if a player is currently in selection mode.
     */
    public boolean isSelecting(Player player) {
        PendingTreasure pending = pendingSelections.get(player.getUniqueId());
        if (pending == null) {
            return false;
        }
        // Check if it's expired
        if (System.currentTimeMillis() > pending.expiresAt) {
            pendingSelections.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /**
     * Get the pending treasure info for a player.
     */
    public PendingTreasure getPending(Player player) {
        return pendingSelections.get(player.getUniqueId());
    }

    /**
     * Complete the selection (called when player clicks a valid block).
     */
    public PendingTreasure completeSelection(Player player) {
        return pendingSelections.remove(player.getUniqueId());
    }

    /**
     * Cancel a player's selection.
     */
    public void cancelSelection(Player player) {
        pendingSelections.remove(player.getUniqueId());
    }

    /**
     * Runs periodically to clean up expired selections and notify players.
     */
    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                pendingSelections.entrySet().removeIf(entry -> {
                    if (now > entry.getValue().expiresAt) {
                        // Notify the player if they're online
                        Player player = plugin.getServer().getPlayer(entry.getKey());
                        if (player != null && player.isOnline()) {
                            config.sendMessage(player, "selection-timeout");
                        }
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
    }

    /**
     * Shutdown the cleanup task.
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        pendingSelections.clear();
    }

    /**
     * Data class holding info about a pending treasure creation.
     */
    public static class PendingTreasure {
        private final String id;
        private final String command;
        private final long expiresAt;

        public PendingTreasure(String id, String command, long expiresAt) {
            this.id = id;
            this.command = command;
            this.expiresAt = expiresAt;
        }

        public String getId() {
            return id;
        }

        public String getCommand() {
            return command;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
