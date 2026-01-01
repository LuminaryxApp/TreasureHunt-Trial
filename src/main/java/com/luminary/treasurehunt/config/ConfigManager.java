package com.luminary.treasurehunt.config;

import com.luminary.treasurehunt.TreasureHunt;
import com.luminary.treasurehunt.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Manages plugin configuration files.
 */
public class ConfigManager {

    private final TreasureHunt plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File configFile;
    private File messagesFile;

    public ConfigManager(TreasureHunt plugin) {
        this.plugin = plugin;
    }

    /**
     * Load all configuration files.
     */
    public void loadAll() {
        // Ensure plugin folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Load config.yml
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        setDefaults(config, "config.yml");

        // Load messages.yml
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        setDefaults(messages, "messages.yml");
    }

    /**
     * Set default values from resource file.
     */
    private void setDefaults(FileConfiguration config, String resourceName) {
        InputStream defaultStream = plugin.getResource(resourceName);
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaults);
        }
    }

    /**
     * Reload all configuration files.
     */
    public void reloadAll() {
        loadAll();
    }

    /**
     * Save the main config.
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save config.yml: " + e.getMessage());
        }
    }

    /**
     * Get the main config.
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Get the messages config.
     */
    public FileConfiguration getMessages() {
        return messages;
    }

    // ==========================================
    //         DATABASE SETTINGS
    // ==========================================

    public String getDatabaseHost() {
        return config.getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        return config.getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.database", "minecraft");
    }

    public String getDatabaseUsername() {
        return config.getString("database.username", "root");
    }

    public String getDatabasePassword() {
        return config.getString("database.password", "");
    }

    public int getPoolMaxSize() {
        return config.getInt("database.pool.maximum-pool-size", 10);
    }

    public int getPoolMinIdle() {
        return config.getInt("database.pool.minimum-idle", 2);
    }

    public long getPoolConnectionTimeout() {
        return config.getLong("database.pool.connection-timeout", 30000);
    }

    public long getPoolIdleTimeout() {
        return config.getLong("database.pool.idle-timeout", 600000);
    }

    public long getPoolMaxLifetime() {
        return config.getLong("database.pool.max-lifetime", 1800000);
    }

    public String getTreasuresTable() {
        return config.getString("database.tables.treasures", "treasure_hunt_treasures");
    }

    public String getCompletionsTable() {
        return config.getString("database.tables.completions", "treasure_hunt_completions");
    }

    // ==========================================
    //         GENERAL SETTINGS
    // ==========================================

    public int getSelectionTimeout() {
        return config.getInt("settings.selection-timeout", 30);
    }

    public boolean isLogClaims() {
        return config.getBoolean("settings.log-claims", true);
    }

    public boolean isAsyncClaimCheck() {
        return config.getBoolean("settings.async-claim-check", true);
    }

    public int getSyncInterval() {
        return config.getInt("settings.sync-interval", 5);
    }

    // ==========================================
    //         SOUNDS
    // ==========================================

    public ConfigurationSection getSoundSection(String key) {
        return config.getConfigurationSection("sounds." + key);
    }

    // ==========================================
    //         PARTICLES
    // ==========================================

    public boolean isParticleEnabled(String key) {
        return config.getBoolean("particles." + key + ".enabled", true);
    }

    public Particle getParticleType(String key) {
        String typeName = config.getString("particles." + key + ".type", "VILLAGER_HAPPY");
        try {
            return Particle.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle type: " + typeName);
            return Particle.HAPPY_VILLAGER;
        }
    }

    public int getParticleCount(String key) {
        return config.getInt("particles." + key + ".count", 30);
    }

    // ==========================================
    //         GUI SETTINGS
    // ==========================================

    public ConfigurationSection getGuiSection(String menuType) {
        return config.getConfigurationSection("gui." + menuType);
    }

    public String getGuiTitle(String menuType) {
        return config.getString("gui." + menuType + ".title", "&6Menu");
    }

    public int getGuiRows(String menuType) {
        return config.getInt("gui." + menuType + ".rows", 6);
    }

    public Material getGuiMaterial(String menuType, String itemKey) {
        String materialName = config.getString("gui." + menuType + "." + itemKey + ".material", "STONE");
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material: " + materialName);
            return Material.STONE;
        }
    }

    public String getGuiItemName(String menuType, String itemKey) {
        return config.getString("gui." + menuType + "." + itemKey + ".name", " ");
    }

    public List<String> getGuiItemLore(String menuType, String itemKey) {
        return config.getStringList("gui." + menuType + "." + itemKey + ".lore");
    }

    public int getGuiSlot(String menuType, String itemKey) {
        return config.getInt("gui." + menuType + "." + itemKey + ".slot", 0);
    }

    // ==========================================
    //         MESSAGES
    // ==========================================

    public String getPrefix() {
        return messages.getString("prefix", "&6[Treasure] &r");
    }

    /**
     * Get a raw message from messages.yml.
     *
     * @param key the message key
     * @return the raw message
     */
    public String getRawMessage(String key) {
        return messages.getString(key, "&cMissing message: " + key);
    }

    /**
     * Get a message with prefix.
     *
     * @param key the message key
     * @return the message with prefix
     */
    public String getMessage(String key) {
        return getPrefix() + getRawMessage(key);
    }

    /**
     * Get a message with prefix and placeholders replaced.
     *
     * @param key          the message key
     * @param placeholders the placeholders to replace
     * @return the message with prefix and placeholders replaced
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        return MessageUtil.replacePlaceholders(getMessage(key), placeholders);
    }

    /**
     * Send a message to a CommandSender.
     *
     * @param sender the recipient
     * @param key    the message key
     */
    public void sendMessage(CommandSender sender, String key) {
        MessageUtil.send(sender, getMessage(key));
    }

    /**
     * Send a message with placeholders to a CommandSender.
     *
     * @param sender       the recipient
     * @param key          the message key
     * @param placeholders the placeholders to replace
     */
    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        MessageUtil.send(sender, getMessage(key, placeholders));
    }
}
