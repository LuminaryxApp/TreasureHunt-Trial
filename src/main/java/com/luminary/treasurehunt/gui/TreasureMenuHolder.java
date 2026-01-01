package com.luminary.treasurehunt.gui;

import com.luminary.treasurehunt.database.Treasure;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holds state for treasure GUI menus.
 * This lets us figure out which menu type we're in when handling clicks.
 */
public class TreasureMenuHolder implements InventoryHolder {

    public enum MenuType {
        LIST,           // Main treasure list
        CONFIRM_DELETE, // Delete confirmation
        COMPLETIONS     // Who found this treasure
    }

    private final MenuType type;
    private Inventory inventory;

    // For pagination in the list menu
    private int page = 0;

    // For menus that operate on a specific treasure
    private Treasure selectedTreasure;

    public TreasureMenuHolder(MenuType type) {
        this.type = type;
    }

    public MenuType getType() {
        return type;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public Treasure getSelectedTreasure() {
        return selectedTreasure;
    }

    public void setSelectedTreasure(Treasure selectedTreasure) {
        this.selectedTreasure = selectedTreasure;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
