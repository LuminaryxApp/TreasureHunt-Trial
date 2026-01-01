package com.luminary.treasurehunt.listener;

import com.luminary.treasurehunt.TreasureHunt;
import com.luminary.treasurehunt.config.ConfigManager;
import com.luminary.treasurehunt.database.Treasure;
import com.luminary.treasurehunt.manager.SelectionManager;
import com.luminary.treasurehunt.manager.TreasureManager;
import com.luminary.treasurehunt.util.MessageUtil;
import com.luminary.treasurehunt.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Listens for block interactions to handle treasure claiming
 * and block selection during treasure creation.
 */
public class TreasureListener implements Listener {

    private final TreasureHunt plugin;
    private final ConfigManager config;
    private final TreasureManager treasureManager;
    private final SelectionManager selectionManager;

    public TreasureListener(TreasureHunt plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.treasureManager = plugin.getTreasureManager();
        this.selectionManager = plugin.getSelectionManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle main hand to avoid double-fires
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // We only care about right-clicking blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player player = event.getPlayer();
        Location location = block.getLocation();

        // Check if player is in selection mode (creating a treasure)
        if (selectionManager.isSelecting(player)) {
            handleSelection(event, player, location);
            return;
        }

        // Check if this block is a treasure
        Treasure treasure = treasureManager.getTreasureAt(location);
        if (treasure != null) {
            handleClaim(event, player, treasure);
        }
    }

    /**
     * Handle block selection for treasure creation.
     */
    private void handleSelection(PlayerInteractEvent event, Player player, Location location) {
        event.setCancelled(true);

        SelectionManager.PendingTreasure pending = selectionManager.completeSelection(player);
        if (pending == null) {
            return; // Shouldn't happen but just in case
        }

        String id = pending.getId();
        String command = pending.getCommand();

        // Check if there's already a treasure at this location
        if (treasureManager.getTreasureAt(location) != null) {
            MessageUtil.send(player, config.getPrefix() + "&cThere's already a treasure at this location!");
            return;
        }

        // Create the treasure
        treasureManager.createTreasure(id, location, command).thenAccept(success -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    config.sendMessage(player, "treasure-created",
                            MessageUtil.placeholders().add("id", id).build());
                    SoundUtil.playSound(player, config.getSoundSection("treasure-created"), plugin.getLogger());
                } else {
                    MessageUtil.send(player, config.getPrefix() + "&cFailed to create treasure. Check console for errors.");
                }
            });
        });
    }

    /**
     * Handle a player trying to claim a treasure.
     */
    private void handleClaim(PlayerInteractEvent event, Player player, Treasure treasure) {
        event.setCancelled(true);

        // Check permission
        if (!player.hasPermission("treasurehunt.claim")) {
            config.sendMessage(player, "no-permission");
            return;
        }

        // Try to claim
        if (config.isAsyncClaimCheck()) {
            // Async claim (recommended for multi-server)
            treasureManager.claimTreasure(player, treasure).thenAccept(result -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    handleClaimResult(player, result);
                });
            });
        } else {
            // Sync claim (faster but blocks main thread briefly)
            plugin.getDatabaseManager().hasCompletedSync(player.getUniqueId(), treasure.getId());
            treasureManager.claimTreasure(player, treasure).thenAccept(result -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    handleClaimResult(player, result);
                });
            });
        }
    }

    private void handleClaimResult(Player player, TreasureManager.ClaimResult result) {
        switch (result) {
            case SUCCESS -> config.sendMessage(player, "treasure-claimed");
            case ALREADY_CLAIMED -> {
                config.sendMessage(player, "already-claimed");
                treasureManager.playAlreadyClaimedSound(player);
            }
            case ERROR -> config.sendMessage(player, "claim-error");
        }
    }

    /**
     * Clean up when a player leaves (cancel any pending selections).
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        selectionManager.cancelSelection(event.getPlayer());
    }
}
