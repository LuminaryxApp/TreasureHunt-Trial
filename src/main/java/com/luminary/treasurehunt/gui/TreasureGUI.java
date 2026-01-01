package com.luminary.treasurehunt.gui;

import com.luminary.treasurehunt.TreasureHunt;
import com.luminary.treasurehunt.config.ConfigManager;
import com.luminary.treasurehunt.database.DatabaseManager;
import com.luminary.treasurehunt.database.Treasure;
import com.luminary.treasurehunt.manager.TreasureManager;
import com.luminary.treasurehunt.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles building and populating the treasure management GUIs.
 * All appearance settings come from config so it's fully customizable.
 */
public class TreasureGUI {

    private final TreasureHunt plugin;
    private final ConfigManager config;
    private final TreasureManager treasureManager;
    private final DatabaseManager database;

    // How many items we can fit per page (exclude border rows)
    private static final int ITEMS_PER_PAGE = 28;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public TreasureGUI(TreasureHunt plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.treasureManager = plugin.getTreasureManager();
        this.database = plugin.getDatabaseManager();
    }

    // ==========================================
    //         LIST MENU
    // ==========================================

    public void openListMenu(Player player, int page) {
        TreasureMenuHolder holder = new TreasureMenuHolder(TreasureMenuHolder.MenuType.LIST);
        holder.setPage(page);

        String title = MessageUtil.colorize(config.getGuiTitle("list-menu"));
        int rows = config.getGuiRows("list-menu");
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        // Fill border
        fillBorder(inv, "list-menu");

        // Get treasures for this page
        List<Treasure> allTreasures = new ArrayList<>(treasureManager.getAllTreasures());
        int totalPages = Math.max(1, (int) Math.ceil(allTreasures.size() / (double) ITEMS_PER_PAGE));
        int startIndex = page * ITEMS_PER_PAGE;

        // Place treasure items in the middle slots
        int slot = 10; // Start after first row + 1
        for (int i = startIndex; i < Math.min(startIndex + ITEMS_PER_PAGE, allTreasures.size()); i++) {
            // Skip border slots
            while (isBorderSlot(slot, rows)) {
                slot++;
            }
            if (slot >= (rows - 1) * 9) break;

            Treasure treasure = allTreasures.get(i);
            inv.setItem(slot, createTreasureItem(treasure));
            slot++;
        }

        // Info item
        int infoSlot = config.getGuiSlot("list-menu", "info");
        inv.setItem(infoSlot, createInfoItem(allTreasures.size(), page + 1, totalPages));

        // Navigation buttons
        if (page > 0) {
            int prevSlot = config.getGuiSlot("list-menu", "previous-page");
            inv.setItem(prevSlot, createNavItem("list-menu", "previous-page"));
        }

        if (page < totalPages - 1) {
            int nextSlot = config.getGuiSlot("list-menu", "next-page");
            inv.setItem(nextSlot, createNavItem("list-menu", "next-page"));
        }

        // Close button
        int closeSlot = config.getGuiSlot("list-menu", "close-button");
        inv.setItem(closeSlot, createNavItem("list-menu", "close-button"));

        player.openInventory(inv);
    }

    private ItemStack createTreasureItem(Treasure treasure) {
        Material material = config.getGuiMaterial("list-menu", "treasure-item");
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Build placeholders
            Map<String, String> placeholders = MessageUtil.placeholders()
                    .add("id", treasure.getId())
                    .add("world", treasure.getWorldName())
                    .add("x", treasure.getX())
                    .add("y", treasure.getY())
                    .add("z", treasure.getZ())
                    .add("command", treasure.getCommand())
                    .add("created_at", DATE_FORMAT.format(treasure.getCreatedAt()))
                    .build();

            String name = config.getGuiItemName("list-menu", "treasure-item");
            meta.displayName(MessageUtil.toComponent(MessageUtil.replacePlaceholders(name, placeholders)));

            List<String> loreTemplate = config.getGuiItemLore("list-menu", "treasure-item");
            List<String> lore = loreTemplate.stream()
                    .map(line -> MessageUtil.replacePlaceholders(line, placeholders))
                    .collect(Collectors.toList());
            meta.lore(lore.stream().map(MessageUtil::toComponent).collect(Collectors.toList()));

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createInfoItem(int total, int page, int maxPage) {
        Material material = config.getGuiMaterial("list-menu", "info");
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            Map<String, String> placeholders = MessageUtil.placeholders()
                    .add("total", total)
                    .add("page", page)
                    .add("max_page", maxPage)
                    .build();

            String name = config.getGuiItemName("list-menu", "info");
            meta.displayName(MessageUtil.toComponent(MessageUtil.replacePlaceholders(name, placeholders)));

            List<String> loreTemplate = config.getGuiItemLore("list-menu", "info");
            List<String> lore = loreTemplate.stream()
                    .map(line -> MessageUtil.replacePlaceholders(line, placeholders))
                    .collect(Collectors.toList());
            meta.lore(lore.stream().map(MessageUtil::toComponent).collect(Collectors.toList()));

            item.setItemMeta(meta);
        }

        return item;
    }

    // ==========================================
    //         CONFIRM DELETE MENU
    // ==========================================

    public void openConfirmMenu(Player player, Treasure treasure) {
        TreasureMenuHolder holder = new TreasureMenuHolder(TreasureMenuHolder.MenuType.CONFIRM_DELETE);
        holder.setSelectedTreasure(treasure);

        String title = MessageUtil.colorize(config.getGuiTitle("confirm-menu"));
        int rows = config.getGuiRows("confirm-menu");
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        // Fill background
        Material bgMaterial = config.getGuiMaterial("confirm-menu", "background");
        ItemStack bg = createSimpleItem(bgMaterial, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, bg);
        }

        Map<String, String> placeholders = MessageUtil.placeholders()
                .add("id", treasure.getId())
                .build();

        // Confirm button
        int confirmSlot = config.getGuiSlot("confirm-menu", "confirm");
        inv.setItem(confirmSlot, createConfigItem("confirm-menu", "confirm", placeholders));

        // Cancel button
        int cancelSlot = config.getGuiSlot("confirm-menu", "cancel");
        inv.setItem(cancelSlot, createConfigItem("confirm-menu", "cancel", placeholders));

        player.openInventory(inv);
    }

    // ==========================================
    //         COMPLETIONS MENU
    // ==========================================

    public void openCompletionsMenu(Player player, Treasure treasure, int page) {
        TreasureMenuHolder holder = new TreasureMenuHolder(TreasureMenuHolder.MenuType.COMPLETIONS);
        holder.setSelectedTreasure(treasure);
        holder.setPage(page);

        Map<String, String> titlePlaceholders = MessageUtil.placeholders()
                .add("id", treasure.getId())
                .build();
        String title = MessageUtil.colorize(
                MessageUtil.replacePlaceholders(config.getGuiTitle("completions-menu"), titlePlaceholders)
        );
        int rows = config.getGuiRows("completions-menu");
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        // Fill border
        fillBorder(inv, "list-menu"); // Reuse list-menu border

        // Get completions
        List<DatabaseManager.CompletionEntry> completions = database.getCompletions(treasure.getId());

        if (completions.isEmpty()) {
            // Show empty placeholder
            Material emptyMat = config.getGuiMaterial("completions-menu", "empty");
            String emptyName = config.getGuiItemName("completions-menu", "empty");
            List<String> emptyLore = config.getGuiItemLore("completions-menu", "empty");

            ItemStack emptyItem = new ItemStack(emptyMat);
            ItemMeta meta = emptyItem.getItemMeta();
            if (meta != null) {
                meta.displayName(MessageUtil.toComponent(emptyName));
                meta.lore(emptyLore.stream().map(MessageUtil::toComponent).collect(Collectors.toList()));
                emptyItem.setItemMeta(meta);
            }
            inv.setItem(22, emptyItem); // Center slot
        } else {
            int startIndex = page * ITEMS_PER_PAGE;
            int slot = 10;

            for (int i = startIndex; i < Math.min(startIndex + ITEMS_PER_PAGE, completions.size()); i++) {
                while (isBorderSlot(slot, rows)) {
                    slot++;
                }
                if (slot >= (rows - 1) * 9) break;

                DatabaseManager.CompletionEntry entry = completions.get(i);
                inv.setItem(slot, createPlayerHead(entry));
                slot++;
            }
        }

        // Back button
        int backSlot = config.getGuiSlot("completions-menu", "back-button");
        inv.setItem(backSlot, createNavItem("completions-menu", "back-button"));

        player.openInventory(inv);
    }

    private ItemStack createPlayerHead(DatabaseManager.CompletionEntry entry) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getPlayerUuid());
            meta.setOwningPlayer(offlinePlayer);

            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : entry.getPlayerUuid().toString();

            Map<String, String> placeholders = MessageUtil.placeholders()
                    .add("player", playerName)
                    .add("completed_at", DATE_FORMAT.format(entry.getCompletedAt()))
                    .build();

            String name = config.getGuiItemName("completions-menu", "player-item");
            meta.displayName(MessageUtil.toComponent(MessageUtil.replacePlaceholders(name, placeholders)));

            List<String> loreTemplate = config.getGuiItemLore("completions-menu", "player-item");
            List<String> lore = loreTemplate.stream()
                    .map(line -> MessageUtil.replacePlaceholders(line, placeholders))
                    .collect(Collectors.toList());
            meta.lore(lore.stream().map(MessageUtil::toComponent).collect(Collectors.toList()));

            head.setItemMeta(meta);
        }

        return head;
    }

    // ==========================================
    //         HELPERS
    // ==========================================

    private void fillBorder(Inventory inv, String menuType) {
        Material material = config.getGuiMaterial(menuType, "border");
        String name = config.getGuiItemName(menuType, "border");
        ItemStack border = createSimpleItem(material, name);

        int size = inv.getSize();
        int rows = size / 9;

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border); // Top row
            inv.setItem(size - 9 + i, border); // Bottom row
        }
        for (int i = 1; i < rows - 1; i++) {
            inv.setItem(i * 9, border); // Left column
            inv.setItem(i * 9 + 8, border); // Right column
        }
    }

    private boolean isBorderSlot(int slot, int rows) {
        int size = rows * 9;
        // Top row
        if (slot < 9) return true;
        // Bottom row
        if (slot >= size - 9) return true;
        // Left or right edge
        return slot % 9 == 0 || slot % 9 == 8;
    }

    private ItemStack createSimpleItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.toComponent(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavItem(String menuType, String itemKey) {
        Material material = config.getGuiMaterial(menuType, itemKey);
        String name = config.getGuiItemName(menuType, itemKey);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.toComponent(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createConfigItem(String menuType, String itemKey, Map<String, String> placeholders) {
        Material material = config.getGuiMaterial(menuType, itemKey);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = config.getGuiItemName(menuType, itemKey);
            meta.displayName(MessageUtil.toComponent(MessageUtil.replacePlaceholders(name, placeholders)));

            List<String> loreTemplate = config.getGuiItemLore(menuType, itemKey);
            if (!loreTemplate.isEmpty()) {
                List<String> lore = loreTemplate.stream()
                        .map(line -> MessageUtil.replacePlaceholders(line, placeholders))
                        .collect(Collectors.toList());
                meta.lore(lore.stream().map(MessageUtil::toComponent).collect(Collectors.toList()));
            }

            item.setItemMeta(meta);
        }

        return item;
    }
}
