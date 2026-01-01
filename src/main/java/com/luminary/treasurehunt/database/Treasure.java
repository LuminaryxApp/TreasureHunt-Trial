package com.luminary.treasurehunt.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Timestamp;

/**
 * Represents a treasure that players can find and claim.
 * Each treasure is tied to a specific block location in the world.
 */
public class Treasure {

    private final String id;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final String command;
    private final Timestamp createdAt;

    public Treasure(String id, String worldName, int x, int y, int z, String command, Timestamp createdAt) {
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.command = command;
        this.createdAt = createdAt;
    }

    // Shorthand constructor for when you just have a location
    public Treasure(String id, Location location, String command) {
        this(id,
             location.getWorld().getName(),
             location.getBlockX(),
             location.getBlockY(),
             location.getBlockZ(),
             command,
             new Timestamp(System.currentTimeMillis()));
    }

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getCommand() {
        return command;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    /**
     * Get the bukkit Location for this treasure.
     * Returns null if the world isn't loaded (shouldn't happen normally).
     */
    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    /**
     * Quick check to see if a location matches this treasure.
     * We compare world name and block coords.
     */
    public boolean matchesLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return location.getWorld().getName().equals(worldName)
                && location.getBlockX() == x
                && location.getBlockY() == y
                && location.getBlockZ() == z;
    }

    /**
     * Get a location string for display purposes (like "world 100, 64, -200")
     */
    public String getLocationString() {
        return worldName + " " + x + ", " + y + ", " + z;
    }

    @Override
    public String toString() {
        return "Treasure{id='" + id + "', location=" + getLocationString() + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Treasure other = (Treasure) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
