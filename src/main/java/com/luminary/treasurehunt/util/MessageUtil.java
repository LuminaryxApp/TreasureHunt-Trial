package com.luminary.treasurehunt.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling message formatting with color codes and placeholders.
 */
public final class MessageUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");

    private MessageUtil() {
        // Utility class
    }

    /**
     * Colorize a string with legacy color codes (&) and hex colors (&#RRGGBB).
     *
     * @param message the message to colorize
     * @return the colorized string
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Convert hex colors (&#RRGGBB) to Bukkit format
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        // Convert legacy color codes
        return buffer.toString().replace('&', '§');
    }

    /**
     * Convert a colorized string to a Component.
     *
     * @param message the message to convert
     * @return the Component
     */
    public static Component toComponent(String message) {
        return LegacyComponentSerializer.legacySection().deserialize(colorize(message));
    }

    /**
     * Replace placeholders in a message.
     *
     * @param message      the message with placeholders
     * @param placeholders the placeholders to replace (key without %, value)
     * @return the message with placeholders replaced
     */
    public static String replacePlaceholders(String message, Map<String, String> placeholders) {
        if (message == null || placeholders == null || placeholders.isEmpty()) {
            return message;
        }

        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    /**
     * Send a colorized message to a CommandSender.
     *
     * @param sender  the recipient
     * @param message the message to send
     */
    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(toComponent(message));
    }

    /**
     * Send a colorized message with placeholders to a CommandSender.
     *
     * @param sender       the recipient
     * @param message      the message to send
     * @param placeholders the placeholders to replace
     */
    public static void send(CommandSender sender, String message, Map<String, String> placeholders) {
        send(sender, replacePlaceholders(message, placeholders));
    }

    /**
     * Create a placeholder map builder.
     *
     * @return a new PlaceholderBuilder
     */
    public static PlaceholderBuilder placeholders() {
        return new PlaceholderBuilder();
    }

    /**
     * Builder for creating placeholder maps.
     */
    public static class PlaceholderBuilder {
        private final Map<String, String> placeholders = new HashMap<>();

        public PlaceholderBuilder add(String key, String value) {
            placeholders.put(key, value != null ? value : "");
            return this;
        }

        public PlaceholderBuilder add(String key, int value) {
            placeholders.put(key, String.valueOf(value));
            return this;
        }

        public PlaceholderBuilder add(String key, long value) {
            placeholders.put(key, String.valueOf(value));
            return this;
        }

        public PlaceholderBuilder add(String key, double value) {
            placeholders.put(key, String.format("%.2f", value));
            return this;
        }

        public Map<String, String> build() {
            return new HashMap<>(placeholders);
        }
    }
}
