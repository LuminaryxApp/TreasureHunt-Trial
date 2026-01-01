package com.luminary.treasurehunt.listener;

import com.luminary.treasurehunt.TreasureHunt;
import com.luminary.treasurehunt.config.ConfigManager;
import com.luminary.treasurehunt.database.Treasure;
import com.luminary.treasurehunt.gui.TreasureGUI;
import com.luminary.treasurehunt.gui.TreasureMenuHolder;
import com.luminary.treasurehunt.manager.TreasureManager;
import com.luminary.treasurehunt.util.MessageUtil;
import com.luminary.treasurehunt.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles clicks in the treasure management GUIs.
 */
public class InventoryListener implements Listener {

    private final TreasureHunt plugin;
    private final ConfigManager config;
    private final TreasureManager treasureManager;
    private final TreasureGUI treasureGUI;

    public InventoryListener(TreasureHunt plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.treasureManager = plugin.getTreasureManager();
        this.treasureGUI = plugin.getTreasureGUI();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (!(holder instanceof TreasureMenuHolder menuHolder)) {
            return;
        }

        // Always cancel clicks in our menus
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Play click sound
        SoundUtil.playSound(player, config.getSoundSection("gui-click"), plugin.getLogger());

        int slot = event.getRawSlot();

        // Make sure they clicked inside the top inventory
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        switch (menuHolder.getType()) {
            case LIST -> handleListClick(player, menuHolder, slot, event.getClick());
            case CONFIRM_DELETE -> handleConfirmClick(player, menuHolder, slot);
            case COMPLETIONS -> handleCompletionsClick(player, menuHolder, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Prevent dragging items in our menus
        if (event.getInventory().getHolder() instanceof TreasureMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleListClick(Player player, TreasureMenuHolder holder, int slot, ClickType clickType) {
        int page = holder.getPage();
        int rows = config.getGuiRows("list-menu");

        // Check navigation buttons
        int prevSlot = config.getGuiSlot("list-menu", "previous-page");
        int nextSlot = config.getGuiSlot("list-menu", "next-page");
        int closeSlot = config.getGuiSlot("list-menu", "close-button");

        if (slot == prevSlot && page > 0) {
            treasureGUI.openListMenu(player, page - 1);
            return;
        }

        if (slot == nextSlot) {
            treasureGUI.openListMenu(player, page + 1);
            return;
        }

        if (slot == closeSlot) {
            player.closeInventory();
            return;
        }

        // Check if they clicked a treasure item
        ItemStack clicked = holder.getInventory().getItem(slot);
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        // Try to find which treasure this is
        Treasure treasure = findTreasureFromItem(clicked);
        if (treasure == null) {
            return;
        }

        if (clickType == ClickType.LEFT) {
            // View completions
            treasureGUI.openCompletionsMenu(player, treasure, 0);
        } else if (clickType == ClickType.RIGHT) {
            // Delete confirmation
            treasureGUI.openConfirmMenu(player, treasure);
        }
    }

    private void handleConfirmClick(Player player, TreasureMenuHolder holder, int slot) {
        int confirmSlot = config.getGuiSlot("confirm-menu", "confirm");
        int cancelSlot = config.getGuiSlot("confirm-menu", "cancel");

        if (slot == confirmSlot) {
            Treasure treasure = holder.getSelectedTreasure();
            if (treasure != null) {
                treasureManager.deleteTreasure(treasure.getId()).thenAccept(success -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (success) {
                            config.sendMessage(player, "treasure-deleted",
                                    MessageUtil.placeholders().add("id", treasure.getId()).build());
                            SoundUtil.playSound(player, config.getSoundSection("treasure-deleted"), plugin.getLogger());
                        }
                        // Go back to list
                        treasureGUI.openListMenu(player, 0);
                    });
                });
            }
        } else if (slot == cancelSlot) {
            // Go back to list
            treasureGUI.openListMenu(player, 0);
        }
    }

    private void handleCompletionsClick(Player player, TreasureMenuHolder holder, int slot) {
        int backSlot = config.getGuiSlot("completions-menu", "back-button");

        if (slot == backSlot) {
            treasureGUI.openListMenu(player, 0);
        }
    }

    /**
     * Try to figure out which treasure a clicked item represents.
     * We do this by extracting the ID from the item's display name.
     */
    private Treasure findTreasureFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }

        // The name format is typically "&e%id%" - we need to extract the ID
        // This is a bit hacky but works for our purposes
        String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(meta.displayName());

        // Try to find a treasure with this ID
        for (Treasure t : treasureManager.getAllTreasures()) {
            if (displayName.contains(t.getId())) {
                return t;
            }
        }

        return null;
    }
}
