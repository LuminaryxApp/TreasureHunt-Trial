package com.luminary.treasurehunt;

import com.luminary.treasurehunt.command.TreasureCommand;
import com.luminary.treasurehunt.config.ConfigManager;
import com.luminary.treasurehunt.database.DatabaseManager;
import com.luminary.treasurehunt.gui.TreasureGUI;
import com.luminary.treasurehunt.listener.InventoryListener;
import com.luminary.treasurehunt.listener.TreasureListener;
import com.luminary.treasurehunt.manager.SelectionManager;
import com.luminary.treasurehunt.manager.TreasureManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TreasureHunt - A customizable treasure hunt plugin with multi-server MySQL support.
 *
 * Place treasures in the world that players can find and claim.
 * Each treasure runs a configurable command when claimed.
 */
public class TreasureHunt extends JavaPlugin {

    private static TreasureHunt instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private TreasureManager treasureManager;
    private SelectionManager selectionManager;
    private TreasureGUI treasureGUI;

    @Override
    public void onEnable() {
        instance = this;

        // Load configs first
        configManager = new ConfigManager(this);
        configManager.loadAll();

        // Initialize database connection
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to connect to database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        treasureManager = new TreasureManager(this);
        treasureManager.loadTreasures();
        treasureManager.startSyncTask();

        selectionManager = new SelectionManager(this);

        // Initialize GUI handler
        treasureGUI = new TreasureGUI(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new TreasureListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

        // Register commands
        TreasureCommand cmd = new TreasureCommand(this);
        getCommand("treasure").setExecutor(cmd);
        getCommand("treasure").setTabCompleter(cmd);

        getLogger().info("TreasureHunt has been enabled!");
    }

    @Override
    public void onDisable() {
        // Stop sync task
        if (treasureManager != null) {
            treasureManager.stopSyncTask();
        }

        // Clean up selection manager
        if (selectionManager != null) {
            selectionManager.shutdown();
        }

        // Close database connections
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("TreasureHunt has been disabled.");
    }

    public static TreasureHunt getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TreasureManager getTreasureManager() {
        return treasureManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public TreasureGUI getTreasureGUI() {
        return treasureGUI;
    }
}
