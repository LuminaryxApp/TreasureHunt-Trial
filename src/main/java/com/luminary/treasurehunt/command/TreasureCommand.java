package com.luminary.treasurehunt.command;

import com.luminary.treasurehunt.TreasureHunt;
import com.luminary.treasurehunt.config.ConfigManager;
import com.luminary.treasurehunt.database.DatabaseManager;
import com.luminary.treasurehunt.database.Treasure;
import com.luminary.treasurehunt.manager.SelectionManager;
import com.luminary.treasurehunt.manager.TreasureManager;
import com.luminary.treasurehunt.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Main command handler for /treasure.
 * Handles all subcommands: create, delete, list, completed, gui, reload, help.
 */
public class TreasureCommand implements CommandExecutor, TabCompleter {

    private final TreasureHunt plugin;
    private final ConfigManager config;
    private final TreasureManager treasureManager;
    private final SelectionManager selectionManager;
    private final DatabaseManager database;

    // Only allow alphanumeric + underscore for treasure IDs
    private static final Pattern VALID_ID = Pattern.compile("^[a-zA-Z0-9_]+$");

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public TreasureCommand(TreasureHunt plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.treasureManager = plugin.getTreasureManager();
        this.selectionManager = plugin.getSelectionManager();
        this.database = plugin.getDatabaseManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "completed" -> handleCompleted(sender, args);
            case "gui" -> handleGui(sender);
            case "reload" -> handleReload(sender);
            case "help" -> showHelp(sender);
            default -> {
                config.sendMessage(sender, "help-header");
                config.sendMessage(sender, "help-footer");
            }
        }

        return true;
    }

    // /treasure create <id> <command...>
    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            config.sendMessage(sender, "player-only");
            return;
        }

        if (!player.hasPermission("treasurehunt.admin")) {
            config.sendMessage(player, "no-permission");
            return;
        }

        if (args.length < 3) {
            MessageUtil.send(player, config.getPrefix() + "&cUsage: /treasure create <id> <command>");
            return;
        }

        String id = args[1];

        // Validate the ID format
        if (!VALID_ID.matcher(id).matches()) {
            config.sendMessage(player, "invalid-id");
            return;
        }

        // Check if ID already exists
        if (treasureManager.treasureExists(id)) {
            config.sendMessage(player, "treasure-already-exists",
                    MessageUtil.placeholders().add("id", id).build());
            return;
        }

        // Combine the rest of the args into the command
        String treasureCommand = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        // Put player into selection mode
        selectionManager.startSelection(player, id, treasureCommand);
        config.sendMessage(player, "selection-started");
    }

    // /treasure delete <id>
    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("treasurehunt.admin")) {
            config.sendMessage(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(sender, config.getPrefix() + "&cUsage: /treasure delete <id>");
            return;
        }

        String id = args[1];

        if (!treasureManager.treasureExists(id)) {
            config.sendMessage(sender, "treasure-not-found",
                    MessageUtil.placeholders().add("id", id).build());
            return;
        }

        treasureManager.deleteTreasure(id).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    config.sendMessage(sender, "treasure-deleted",
                            MessageUtil.placeholders().add("id", id).build());
                } else {
                    MessageUtil.send(sender, config.getPrefix() + "&cFailed to delete treasure.");
                }
            });
        });
    }

    // /treasure list
    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("treasurehunt.admin")) {
            config.sendMessage(sender, "no-permission");
            return;
        }

        Collection<Treasure> treasures = treasureManager.getAllTreasures();

        config.sendMessage(sender, "list-header",
                MessageUtil.placeholders().add("count", treasures.size()).build());

        if (treasures.isEmpty()) {
            MessageUtil.send(sender, config.getRawMessage("list-empty"));
        } else {
            for (Treasure t : treasures) {
                MessageUtil.send(sender, MessageUtil.replacePlaceholders(
                        config.getRawMessage("list-entry"),
                        MessageUtil.placeholders()
                                .add("id", t.getId())
                                .add("world", t.getWorldName())
                                .add("x", t.getX())
                                .add("y", t.getY())
                                .add("z", t.getZ())
                                .build()
                ));
            }
        }

        MessageUtil.send(sender, config.getRawMessage("list-footer"));
    }

    // /treasure completed <id>
    private void handleCompleted(CommandSender sender, String[] args) {
        if (!sender.hasPermission("treasurehunt.admin")) {
            config.sendMessage(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(sender, config.getPrefix() + "&cUsage: /treasure completed <id>");
            return;
        }

        String id = args[1];

        if (!treasureManager.treasureExists(id)) {
            config.sendMessage(sender, "treasure-not-found",
                    MessageUtil.placeholders().add("id", id).build());
            return;
        }

        // Fetch completions from database
        List<DatabaseManager.CompletionEntry> completions = database.getCompletions(id);

        config.sendMessage(sender, "completed-header",
                MessageUtil.placeholders()
                        .add("id", id)
                        .add("count", completions.size())
                        .build());

        if (completions.isEmpty()) {
            MessageUtil.send(sender, config.getRawMessage("completed-empty"));
        } else {
            for (DatabaseManager.CompletionEntry entry : completions) {
                // Try to get the player name
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getPlayerUuid());
                String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : entry.getPlayerUuid().toString();

                MessageUtil.send(sender, MessageUtil.replacePlaceholders(
                        config.getRawMessage("completed-entry"),
                        MessageUtil.placeholders()
                                .add("player", playerName)
                                .add("completed_at", DATE_FORMAT.format(entry.getCompletedAt()))
                                .build()
                ));
            }
        }

        MessageUtil.send(sender, config.getRawMessage("completed-footer"));
    }

    // /treasure gui
    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            config.sendMessage(sender, "player-only");
            return;
        }

        if (!player.hasPermission("treasurehunt.admin")) {
            config.sendMessage(player, "no-permission");
            return;
        }

        plugin.getTreasureGUI().openListMenu(player, 0);
    }

    // /treasure reload
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("treasurehunt.admin")) {
            config.sendMessage(sender, "no-permission");
            return;
        }

        config.reloadAll();
        treasureManager.loadTreasures();
        config.sendMessage(sender, "config-reloaded");
    }

    private void showHelp(CommandSender sender) {
        MessageUtil.send(sender, config.getRawMessage("help-header"));
        MessageUtil.send(sender, config.getRawMessage("help-create"));
        MessageUtil.send(sender, config.getRawMessage("help-delete"));
        MessageUtil.send(sender, config.getRawMessage("help-list"));
        MessageUtil.send(sender, config.getRawMessage("help-completed"));
        MessageUtil.send(sender, config.getRawMessage("help-gui"));
        MessageUtil.send(sender, config.getRawMessage("help-reload"));
        MessageUtil.send(sender, config.getRawMessage("help-footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("help", "list"));
            if (sender.hasPermission("treasurehunt.admin")) {
                completions.addAll(Arrays.asList("create", "delete", "completed", "gui", "reload"));
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if ((sub.equals("delete") || sub.equals("completed")) && sender.hasPermission("treasurehunt.admin")) {
                // Suggest existing treasure IDs
                completions.addAll(
                        treasureManager.getAllTreasures().stream()
                                .map(Treasure::getId)
                                .collect(Collectors.toList())
                );
            }
        }

        // Filter by what they've typed so far
        String prefix = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }
}
