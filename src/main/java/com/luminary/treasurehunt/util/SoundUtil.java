package com.luminary.treasurehunt.util;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Utility class for playing sounds from configuration.
 */
public final class SoundUtil {

    private SoundUtil() {
        // Utility class
    }

    /**
     * Play a sound to a player from a configuration section.
     *
     * @param player  the player to play the sound to
     * @param section the configuration section containing sound, volume, pitch
     * @param logger  the logger for error messages
     */
    public static void playSound(Player player, ConfigurationSection section, Logger logger) {
        if (player == null || section == null) {
            return;
        }

        String soundName = section.getString("sound", "");
        if (soundName.isEmpty()) {
            return; // Sound disabled
        }

        float volume = (float) section.getDouble("volume", 1.0);
        float pitch = (float) section.getDouble("pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            if (logger != null) {
                logger.warning("Invalid sound: " + soundName);
            }
        }
    }

    /**
     * Play a sound at a location from a configuration section.
     *
     * @param location the location to play the sound at
     * @param section  the configuration section containing sound, volume, pitch
     * @param logger   the logger for error messages
     */
    public static void playSound(Location location, ConfigurationSection section, Logger logger) {
        if (location == null || location.getWorld() == null || section == null) {
            return;
        }

        String soundName = section.getString("sound", "");
        if (soundName.isEmpty()) {
            return; // Sound disabled
        }

        float volume = (float) section.getDouble("volume", 1.0);
        float pitch = (float) section.getDouble("pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            if (logger != null) {
                logger.warning("Invalid sound: " + soundName);
            }
        }
    }

    /**
     * Play a sound to a player with explicit parameters.
     *
     * @param player the player to play the sound to
     * @param sound  the sound to play
     * @param volume the volume (0.0 - 1.0+)
     * @param pitch  the pitch (0.5 - 2.0)
     */
    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
