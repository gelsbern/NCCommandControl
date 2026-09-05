package org.nubcraft.nccommandcontrol;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NCCommandControl extends JavaPlugin
        implements Listener, TabCompleter {

    private static final String PROXY_CHANNEL = "nubcraft:commandcontrol";
    private static final Set<String> PLAYER_TARGET_FIRST = Set.of(
            "tell", "w", "m", "t", "pm", "message", "msg", "whisper",
            "ignore", "bring", "god", "modreq-ban", "modreq-unban"
    );

    private static final Set<String> CHAT_MESSAGE_COMMANDS = Set.of(
            "sh", "y", "mch", "pvp",
            "tell", "w", "m", "t", "pm", "message", "msg", "whisper",
            "reply", "r", "me",
            "tg", "tm", "ts", "ta", "sa"
    );

    private static final List<String> SAFE_CHANNEL_LIST = List.of(
            "global", "market", "pvp",
            "staff", "moderator", "seniormoderator", "admin", "senioradmin"
    );

    private static final Map<String, String> CITIZEN_WORLDEDIT_PERMISSIONS = Map.ofEntries(
            Map.entry("pos1", "worldedit.selection.pos"),
            Map.entry("pos2", "worldedit.selection.pos"),
            Map.entry("hpos1", "worldedit.selection.hpos"),
            Map.entry("hpos2", "worldedit.selection.hpos"),
            Map.entry("expand", "worldedit.selection.expand"),
            Map.entry("contract", "worldedit.selection.contract"),
            Map.entry("size", "worldedit.selection.size"),
            Map.entry("shift", "worldedit.selection.shift"),
            Map.entry("outset", "worldedit.selection.outset"),
            Map.entry("inset", "worldedit.selection.inset"),
            Map.entry("chunk", "worldedit.selection.chunk"),
            Map.entry("sel", "worldedit.analysis.sel"),
            Map.entry("wand", "worldedit.wand")
    );

    private static final Map<String, String> CITIZEN_WORLDEDIT_USAGE = Map.ofEntries(
            Map.entry("pos1", "//pos1 [coordinates]"),
            Map.entry("pos2", "//pos2 [coordinates]"),
            Map.entry("hpos1", "//hpos1"),
            Map.entry("hpos2", "//hpos2"),
            Map.entry("expand", "//expand <vert|amount> [reverseAmount] [direction]"),
            Map.entry("contract", "//contract <amount> [reverseAmount] [direction]"),
            Map.entry("size", "//size"),
            Map.entry("shift", "//shift <amount> [direction]"),
            Map.entry("outset", "//outset [-hv] <amount>"),
            Map.entry("inset", "//inset [-hv] <amount>"),
            Map.entry("chunk", "//chunk [-cs] [coordinates]"),
            Map.entry("sel", "//sel [selector]"),
            Map.entry("wand", "//wand")
    );

    private static final Map<String, String> HELP_DESCRIPTIONS = Map.ofEntries(
            Map.entry("ch", "View or manage your ChatControl chat channels."),
            Map.entry("channel", "View or manage your ChatControl chat channels."),
            Map.entry("msg", "Send a private message to another player."),
            Map.entry("r", "Reply to the most recent private message."),
            Map.entry("ignore", "Toggle whether you ignore messages from a player."),
            Map.entry("me", "Send a message written as an action, such as '* Player waves'."),
            Map.entry("sh", "Send a message to Global Chat. Alias: /y."),
            Map.entry("y", "Send a message to Global Chat. Alias: /sh."),
            Map.entry("mch", "Send a message to Market Chat."),
            Map.entry("pvp", "Send a message to PvP Chat."),
            Map.entry("pos1", "Set WorldEdit selection position 1."),
            Map.entry("pos2", "Set WorldEdit selection position 2."),
            Map.entry("hpos1", "Set selection position 1 to the block you are looking at."),
            Map.entry("hpos2", "Set selection position 2 to the block you are looking at."),
            Map.entry("expand", "Expand your WorldEdit selection."),
            Map.entry("contract", "Contract your WorldEdit selection."),
            Map.entry("size", "Show information about your current WorldEdit selection."),
            Map.entry("shift", "Move your WorldEdit selection without moving blocks."),
            Map.entry("outset", "Expand your selection in all directions."),
            Map.entry("inset", "Contract your selection in all directions."),
            Map.entry("chunk", "Select the current chunk or specified chunk coordinates."),
            Map.entry("sel", "Choose the WorldEdit region selector type."),
            Map.entry("wand", "Get the WorldEdit selection wand."),
            Map.entry("tg", "Send a message to Staff Chat."),
            Map.entry("tm", "Send a message to Moderator Chat."),
            Map.entry("ts", "Send a message to Senior Moderator Chat."),
            Map.entry("ta", "Send a message to Admin Chat."),
            Map.entry("sa", "Send a message to Senior Admin Chat."),
            Map.entry("rg", "Manage WorldGuard regions available to your rank."),
            Map.entry("chopper", "Allow or block hopper automation for a protected container."),
            Map.entry("credstone", "Allow or block redstone activation for a protected block.")
    );

    private final Set<String> publicCommands = new HashSet<>();
    private final Set<String> preCitizenCommands = new HashSet<>();
    private final Set<String> hiddenCommands = new HashSet<>();
    private final Set<String> tabHiddenCommands = new HashSet<>();
    private final Set<String> blockedSubcommands = new HashSet<>();
    private final Map<String, String> permissionOverrides = new HashMap<>();
    private final Map<UUID, Set<String>> publishedCommands = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> visiblePlayerNames = new ConcurrentHashMap<>();

    private String basePermission;
    private int helpPageSize;
    private BukkitTask refreshTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPolicy();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this,
                PROXY_CHANNEL
        );

        PluginCommand help = getCommand("nchelp");
        if (help == null) {
            throw new IllegalStateException("nchelp is missing from plugin.yml");
        }

        help.setExecutor(this::executeHelp);
        help.setTabCompleter(this);

        for (String commandName : List.of("chopper", "credstone")) {
            PluginCommand toggle = getCommand(commandName);
            if (toggle == null) {
                throw new IllegalStateException(
                        commandName + " is missing from plugin.yml"
                );
            }

            toggle.setExecutor(this::executeBoltToggle);
            toggle.setTabCompleter(this::completeBoltToggle);
        }

        for (Player player : getServer().getOnlinePlayers()) {
            publishCommands(player, true);
            player.updateCommands();
        }

        refreshTask = getServer().getScheduler().runTaskTimer(
                this,
                this::refreshPublishedCommands,
                40L,
                40L
        );

        getLogger().info("Permission-aware command visibility enabled.");
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }

        getServer().getMessenger().unregisterOutgoingPluginChannel(
                this,
                PROXY_CHANNEL
        );
        publishedCommands.clear();
        visiblePlayerNames.clear();
    }

    private void reloadPolicy() {
        reloadConfig();

        basePermission = getConfig().getString(
                "base-player-permission",
                "nubcraft.citizen"
        );

        helpPageSize = Math.max(
                5,
                getConfig().getInt("help-page-size", 10)
        );

        loadSet("public-commands", publicCommands);
        loadSet("pre-citizen-commands", preCitizenCommands);
        loadSet("hidden-commands", hiddenCommands);
        loadSet("tab-hidden-commands", tabHiddenCommands);
        loadSet("blocked-subcommands", blockedSubcommands);

        permissionOverrides.clear();
        for (String key : getConfig()
                .getConfigurationSection("permission-overrides") == null
                ? Set.<String>of()
                : getConfig()
                        .getConfigurationSection("permission-overrides")
                        .getKeys(false)) {

            String permission = getConfig().getString(
                    "permission-overrides." + key,
                    ""
            );

            if (!permission.isBlank()) {
                permissionOverrides.put(
                        normalize(key),
                        permission.trim()
                );
            }
        }
    }

    private void loadSet(String path, Set<String> target) {
        target.clear();
        for (String value : getConfig().getStringList(path)) {
            target.add(normalize(value));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        getServer().getScheduler().runTask(
                this,
                () -> {
                    publishCommands(event.getPlayer(), true);
                    event.getPlayer().updateCommands();
                }
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        publishedCommands.remove(event.getPlayer().getUniqueId());
        visiblePlayerNames.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandList(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();

        if (isBypass(player)) {
            return;
        }

        event.getCommands().removeIf(
                command -> !isVisible(player, command)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBrigadierCommandList(AsyncPlayerSendCommandsEvent<?> event) {
        Player player = event.getPlayer();

        if (isBypass(player)) {
            return;
        }

        event.getCommandNode().getChildren().removeIf(
                node -> !isVisible(player, node.getName())
        );
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        String message = event.getMessage();
        if (message.length() <= 1) {
            return;
        }

        // WorldEdit uses a leading slash as part of many command names, so
        // players type them with two slashes. Keep newcomer restrictions intact
        // and provide a curated Citizen-only //help instead of exposing the
        // entire WorldEdit help tree.
        if (message.startsWith("//") && !isBypass(player)) {
            if (!hasBaseAccess(player)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Unknown command.");
                return;
            }

            String worldEditBody = message.substring(2).trim();
            String[] worldEditParts = worldEditBody.isEmpty()
                    ? new String[0]
                    : worldEditBody.split("\\s+");

            if (worldEditParts.length > 0
                    && normalize(worldEditParts[0]).equals("help")) {
                event.setCancelled(true);
                showCitizenWorldEditHelp(player, worldEditParts);
                return;
            }
        }

        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length == 0) {
            return;
        }

        String root = normalize(parts[0]);

        // ChatControl /ch list exposes every channel and its player roster,
        // including vanished players. NubCraft replaces it with a privacy-safe
        // list containing only channels this player can actually access.
        if ((root.equals("ch") || root.equals("channel"))
                && parts.length >= 2
                && (normalize(parts[1]).equals("list")
                    || normalize(parts[1]).equals("ls"))) {
            event.setCancelled(true);
            showSafeChannelList(player);
            return;
        }

        // Local chat is permanent. It is automatically joined for read/write
        // and is never a channel players should manually join or leave.
        if ((root.equals("ch") || root.equals("channel"))
                && parts.length >= 3
                && (normalize(parts[1]).equals("join")
                    || normalize(parts[1]).equals("leave"))
                && normalize(parts[2]).equals("local")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.GRAY + "Local chat is always enabled and cannot be joined or left manually.");
            return;
        }

        if (isBypass(player)) {
            return;
        }

        // EssentialsX treats "/afk help" as a custom AFK message.
        // NubCraft reserves that spelling as contextual help instead.
        if (root.equals("afk")
                && parts.length == 2
                && normalize(parts[1]).equals("help")) {
            event.setCancelled(true);
            player.performCommand("help afk");
            return;
        }

        if (!isAllowed(player, root)
                || containsBlockedSubcommand(parts)) {

            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Unknown command.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player)) {
            return;
        }

        // Plain/local chat self-completion is hidden for every player,
        // including staff with the command-visibility bypass permission.
        if (!event.isCommand()) {
            String self = player.getName();
            event.setCompletions(event.getCompletions().stream()
                    .filter(completion -> !completion.equalsIgnoreCase(self))
                    .toList());
            event.setHandled(true);
            return;
        }

        if (isBypass(player)) {
            return;
        }

        String buffer = event.getBuffer();

        if (buffer.startsWith("//")) {
            handleCitizenWorldEditAsyncTab(event, player, buffer);
            return;
        }

        String stripped = buffer.startsWith("/")
                ? buffer.substring(1)
                : buffer;
        String[] parts = stripped.split("\s+", -1);

        Set<String> visible = publishedCommands.get(player.getUniqueId());
        if (visible == null) {
            return;
        }

        if (parts.length == 1) {
            String prefix = normalize(parts[0]);
            event.setCompletions(
                    visible.stream()
                            .filter(name -> name.startsWith(prefix))
                            .sorted()
                            .toList()
            );
            event.setHandled(true);
            return;
        }

        if (parts.length >= 2
                && normalize(parts[0]).equals("help")) {
            String prefix = normalize(parts[parts.length - 1]);
            event.setCompletions(
                    visible.stream()
                            .filter(name -> name.startsWith(prefix))
                            .sorted()
                            .toList()
            );
            event.setHandled(true);
            return;
        }

        String root = normalize(parts[0]);

        if ((root.equals("ch") || root.equals("channel"))
                && parts.length == 3
                && (normalize(parts[1]).equals("join")
                    || normalize(parts[1]).equals("leave"))) {
            String action = normalize(parts[1]);
            String prefix = normalize(parts[2]);
            event.setCompletions(SAFE_CHANNEL_LIST.stream()
                    .filter(channel -> action.equals("join")
                            ? canJoinListedChannel(player, channel)
                            : canLeaveListedChannel(player, channel))
                    .filter(channel -> channel.startsWith(prefix))
                    .toList());
            event.setHandled(true);
            return;
        }

        // Doctor and Death Hound intentionally expose their list subcommand
        // to every Citizen even when the player does not own the NCRanks perk.
        if (parts.length == 2
                && ((root.equals("doc")
                        && !player.hasPermission("ncranks.service.dr"))
                || (root.equals("dhound")
                        && !player.hasPermission("ncranks.death.hound")))) {
            String prefix = normalize(parts[1]);
            event.setCompletions("list".startsWith(prefix)
                    ? List.of("list")
                    : List.of());
            event.setHandled(true);
            return;
        }

        boolean playerArgument = PLAYER_TARGET_FIRST.contains(root)
                && parts.length == 2;
        boolean gamemodePlayerArgument = root.equals("gamemode")
                && parts.length == 3;
        boolean chatMessageArgument = CHAT_MESSAGE_COMMANDS.contains(root)
                && parts.length >= 2;

        if (playerArgument || gamemodePlayerArgument || chatMessageArgument) {
            String prefix = parts[parts.length - 1];

            // For chat text, do not dump the player list merely because TAB
            // was pressed after a space. Wait until the sender has typed at
            // least the first character of a name.
            if (chatMessageArgument && prefix.isEmpty()) {
                event.setCompletions(List.of());
                event.setHandled(true);
                return;
            }

            String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
            Set<String> names = visiblePlayerNames.getOrDefault(
                    player.getUniqueId(),
                    Set.of()
            );
            event.setCompletions(
                    names.stream()
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList()
            );
            event.setHandled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player)) {
            return;
        }

        String buffer = event.getBuffer();

        // Fallback for servers/plugins that route local-chat completion
        // through the synchronous event instead of AsyncTabCompleteEvent.
        if (!buffer.startsWith("/")) {
            String self = player.getName();
            event.setCompletions(event.getCompletions().stream()
                    .filter(completion -> !completion.equalsIgnoreCase(self))
                    .toList());
            return;
        }

        if (isBypass(player)) {
            return;
        }

        if (buffer.startsWith("//")) {
            handleCitizenWorldEditTab(event, player, buffer);
            return;
        }

        String stripped = buffer.startsWith("/")
                ? buffer.substring(1)
                : buffer;
        String[] parts = stripped.split("\\s+", -1);

        if (parts.length == 1) {
            String prefix = normalize(parts[0]);
            event.setCompletions(
                    visibleCommands(player).stream()
                            .filter(name -> name.startsWith(prefix))
                            .toList()
            );
            return;
        }

        if (parts.length >= 2
                && normalize(parts[0]).equals("help")) {

            String prefix = normalize(parts[parts.length - 1]);
            event.setCompletions(
                    visibleCommands(player).stream()
                            .filter(name -> name.startsWith(prefix))
                            .toList()
            );
            return;
        }

        String root = normalize(parts[0]);

        if ((root.equals("ch") || root.equals("channel"))
                && parts.length == 3
                && (normalize(parts[1]).equals("join")
                    || normalize(parts[1]).equals("leave"))) {
            String action = normalize(parts[1]);
            String prefix = normalize(parts[2]);
            event.setCompletions(SAFE_CHANNEL_LIST.stream()
                    .filter(channel -> action.equals("join")
                            ? canJoinListedChannel(player, channel)
                            : canLeaveListedChannel(player, channel))
                    .filter(channel -> channel.startsWith(prefix))
                    .toList());
            return;
        }

        // Doctor and Death Hound intentionally expose their list subcommand
        // to every Citizen even when the player does not own the NCRanks perk.
        if (parts.length == 2
                && ((root.equals("doc")
                        && !player.hasPermission("ncranks.service.dr"))
                || (root.equals("dhound")
                        && !player.hasPermission("ncranks.death.hound")))) {
            String prefix = normalize(parts[1]);
            event.setCompletions("list".startsWith(prefix)
                    ? List.of("list")
                    : List.of());
            return;
        }

        boolean playerArgument = PLAYER_TARGET_FIRST.contains(root)
                && parts.length == 2;
        boolean gamemodePlayerArgument = root.equals("gamemode")
                && parts.length == 3;
        boolean chatMessageArgument = CHAT_MESSAGE_COMMANDS.contains(root)
                && parts.length >= 2;

        if (playerArgument || gamemodePlayerArgument || chatMessageArgument) {
            String prefix = parts[parts.length - 1];

            if (chatMessageArgument && prefix.isEmpty()) {
                event.setCompletions(List.of());
                return;
            }

            String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
            Set<String> names = visiblePlayerNames.getOrDefault(
                    player.getUniqueId(),
                    Set.of()
            );
            event.setCompletions(
                    names.stream()
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList()
            );
            return;
        }

        List<String> filtered = new ArrayList<>();
        for (String completion : event.getCompletions()) {
            String normalized = normalize(completion);

            if (blockedSubcommands.contains(normalized)) {
                continue;
            }

            Player target = Bukkit.getPlayerExact(completion);
            if (target != null
                    && (player.getUniqueId().equals(target.getUniqueId())
                    || !player.canSee(target))) {
                continue;
            }

            filtered.add(completion);
        }

        event.setCompletions(filtered);
    }

    private List<String> availableCitizenWorldEditCommands(Player player) {
        return CITIZEN_WORLDEDIT_PERMISSIONS.entrySet().stream()
                .filter(entry -> player.hasPermission(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private void showCitizenWorldEditHelp(Player player, String[] parts) {
        if (parts.length >= 2) {
            String requested = normalize(parts[1]);
            String permission = CITIZEN_WORLDEDIT_PERMISSIONS.get(requested);

            if (permission == null || !player.hasPermission(permission)) {
                player.sendMessage(ChatColor.RED + "Unknown command.");
                return;
            }

            player.sendMessage(ChatColor.GOLD + "//" + requested);
            player.sendMessage(ChatColor.GRAY
                    + HELP_DESCRIPTIONS.getOrDefault(requested, "WorldEdit selection command."));
            String usage = CITIZEN_WORLDEDIT_USAGE.get(requested);
            if (usage != null) {
                player.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + usage);
            }
            return;
        }

        List<String> commands = availableCitizenWorldEditCommands(player);

        player.sendMessage(ChatColor.GOLD + "NubCraft WorldEdit Selection Help");
        player.sendMessage(ChatColor.GRAY
                + "These commands are available for creating and adjusting land-claim selections.");

        for (String command : commands) {
            player.sendMessage(
                    ChatColor.YELLOW + "//" + command
                            + ChatColor.DARK_GRAY + " - "
                            + ChatColor.GRAY
                            + HELP_DESCRIPTIONS.getOrDefault(command, "WorldEdit selection command.")
            );
        }

        player.sendMessage(ChatColor.GRAY + "Use "
                + ChatColor.WHITE + "//help <command>"
                + ChatColor.GRAY + " for usage.");
    }

    private void handleCitizenWorldEditAsyncTab(
            AsyncTabCompleteEvent event,
            Player player,
            String buffer) {

        String body = buffer.substring(2);
        String[] parts = body.split("\\s+", -1);

        if (parts.length == 1) {
            String prefix = normalize(parts[0]);
            List<String> completions = new ArrayList<>();
            if ("help".startsWith(prefix)) {
                completions.add("/help");
            }
            for (String command : availableCitizenWorldEditCommands(player)) {
                if (command.startsWith(prefix)) {
                    completions.add("/" + command);
                }
            }
            event.setCompletions(completions);
            event.setHandled(true);
            return;
        }

        String root = normalize(parts[0]);
        if (root.equals("help")) {
            if (parts.length == 2) {
                String prefix = normalize(parts[1]);
                event.setCompletions(
                        availableCitizenWorldEditCommands(player).stream()
                                .filter(command -> command.startsWith(prefix))
                                .toList()
                );
            } else {
                event.setCompletions(List.of());
            }
            event.setHandled(true);
            return;
        }

        String permission = CITIZEN_WORLDEDIT_PERMISSIONS.get(root);
        if (permission == null || !player.hasPermission(permission)) {
            event.setCompletions(List.of());
            event.setHandled(true);
        }
        // For allowed commands, preserve FAWE native argument completion
        // (directions, selectors, flags, vert, and so on).
    }

    private void handleCitizenWorldEditTab(
            TabCompleteEvent event,
            Player player,
            String buffer) {

        String body = buffer.substring(2);
        String[] parts = body.split("\\s+", -1);

        if (parts.length == 1) {
            String prefix = normalize(parts[0]);
            List<String> completions = new ArrayList<>();
            if ("help".startsWith(prefix)) {
                completions.add("/help");
            }
            for (String command : availableCitizenWorldEditCommands(player)) {
                if (command.startsWith(prefix)) {
                    completions.add("/" + command);
                }
            }
            event.setCompletions(completions);
            return;
        }

        String root = normalize(parts[0]);
        if (root.equals("help")) {
            if (parts.length == 2) {
                String prefix = normalize(parts[1]);
                event.setCompletions(
                        availableCitizenWorldEditCommands(player).stream()
                                .filter(command -> command.startsWith(prefix))
                                .toList()
                );
            } else {
                event.setCompletions(List.of());
            }
            return;
        }

        String permission = CITIZEN_WORLDEDIT_PERMISSIONS.get(root);
        if (permission == null || !player.hasPermission(permission)) {
            event.setCompletions(List.of());
        }
        // Allowed WorldEdit argument completions are left to FAWE.
    }

    private void cacheVisiblePlayers(Player player) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Player target : getServer().getOnlinePlayers()) {
            if (!player.getUniqueId().equals(target.getUniqueId()) && player.canSee(target)) {
                names.add(target.getName());
            }
        }
        visiblePlayerNames.put(player.getUniqueId(), Set.copyOf(names));
    }

    private void refreshPublishedCommands() {
        for (Player player : getServer().getOnlinePlayers()) {
            publishCommands(player, false);
        }
    }

    private void publishCommands(Player player, boolean force) {
        cacheVisiblePlayers(player);
        Set<String> visible = new TreeSet<>(visibleCommands(player));
        Set<String> previous = publishedCommands.get(player.getUniqueId());

        if (!force && visible.equals(previous)) {
            return;
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF("VISIBLE");
                output.writeUTF(player.getUniqueId().toString());
                output.writeInt(visible.size());
                for (String command : visible) {
                    output.writeUTF(command);
                }
            }

            player.sendPluginMessage(
                    this,
                    PROXY_CHANNEL,
                    bytes.toByteArray()
            );
            publishedCommands.put(
                    player.getUniqueId(),
                    Set.copyOf(visible)
            );

            if (!force) {
                player.updateCommands();
            }

        } catch (IOException exception) {
            getLogger().warning(
                    "Could not publish command policy for "
                            + player.getName() + ": "
                            + exception.getMessage()
            );
        }
    }

    private void showSafeChannelList(Player player) {
        List<String> channels = SAFE_CHANNEL_LIST.stream()
                .filter(channel -> canAccessListedChannel(player, channel))
                .toList();

        player.sendMessage(ChatColor.GOLD + "Available Chat Channels");
        if (channels.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No optional channels are currently available.");
            return;
        }

        for (String channel : channels) {
            player.sendMessage(ChatColor.YELLOW + "- " + ChatColor.WHITE + channel);
        }

        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/ch join <channel>" + ChatColor.GRAY + " to join a channel.");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/ch leave <channel>" + ChatColor.GRAY + " to leave a channel.");
    }

    private boolean canAccessListedChannel(Player player, String channel) {
        return canJoinListedChannel(player, channel)
                || canLeaveListedChannel(player, channel);
    }

    private boolean canJoinListedChannel(Player player, String channel) {
        String prefix = "chatcontrol.channel.join." + channel;
        return player.hasPermission(prefix)
                || player.hasPermission(prefix + ".read")
                || player.hasPermission(prefix + ".write");
    }

    private boolean canLeaveListedChannel(Player player, String channel) {
        return player.hasPermission("chatcontrol.channel.leave." + channel);
    }

    private boolean executeBoltToggle(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is available only in game.");
            return true;
        }

        if (!hasBaseAccess(player)) {
            player.sendMessage(ChatColor.RED + "Unknown command.");
            return true;
        }

        if (args.length != 1
                || (!args[0].equalsIgnoreCase("on")
                    && !args[0].equalsIgnoreCase("off"))) {
            player.sendMessage(ChatColor.YELLOW + "Usage: "
                    + ChatColor.WHITE + "/" + label + " <on|off>");
            return true;
        }

        if (!getServer().getPluginManager().isPluginEnabled("Bolt")) {
            player.sendMessage(ChatColor.RED
                    + "Container protection is currently unavailable.");
            return true;
        }

        boolean enable = args[0].equalsIgnoreCase("on");
        String root = normalize(command.getName());
        String access = root.equals("chopper") ? "hopper" : "redstone";
        String source = root.equals("chopper") ? "block" : "redstone";
        String boltCommand = "bolt modify "
                + (enable ? "add " : "remove ")
                + access + " " + source;

        if (!player.performCommand(boltCommand)) {
            player.sendMessage(ChatColor.RED
                    + "Could not start the Bolt protection change.");
        }

        return true;
    }

    private List<String> completeBoltToggle(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {

        if (!(sender instanceof Player)
                || args.length != 1) {
            return List.of();
        }

        String prefix = normalize(args[0]);
        return List.of("on", "off").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }

    private boolean executeHelp(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player help is available in game.");
            return true;
        }

        if (!isBypass(player)
                && !hasBaseAccess(player)) {
            player.sendMessage(ChatColor.RED + "Unknown command.");
            return true;
        }

        if (args.length == 0) {
            showHelpPage(player, 1);
            return true;
        }

        if (args.length == 1) {
            try {
                int page = Integer.parseInt(args[0]);
                showHelpPage(player, page);
                return true;
            } catch (NumberFormatException ignored) {
                // Treat it as a command name.
            }
        }

        String requested = normalize(args[0]);
        if (!isBypass(player)
                && !isVisible(player, requested)) {
            player.sendMessage(ChatColor.RED + "Unknown command.");
            return true;
        }

        String worldEditPermission = CITIZEN_WORLDEDIT_PERMISSIONS.get(requested);
        if (worldEditPermission != null
                && (isBypass(player) || player.hasPermission(worldEditPermission))) {
            player.sendMessage(ChatColor.GOLD + "//" + requested);
            player.sendMessage(ChatColor.GRAY
                    + HELP_DESCRIPTIONS.getOrDefault(requested, "WorldEdit selection command."));
            String worldEditUsage = CITIZEN_WORLDEDIT_USAGE.get(requested);
            if (worldEditUsage != null) {
                player.sendMessage(ChatColor.YELLOW + "Usage: "
                        + ChatColor.WHITE + worldEditUsage);
            }
            return true;
        }

        Command target = getServer()
                .getCommandMap()
                .getCommand(requested);

        if (target == null && requested.equals("help")) {
            showHelpPage(player, 1);
            return true;
        }

        if (target == null) {
            player.sendMessage(ChatColor.RED + "Unknown command.");
            return true;
        }

        player.sendMessage(
                ChatColor.GOLD + "/" + requested
        );

        String description = helpDescription(requested, target);
        if (description == null || description.isBlank()) {
            description = "Available command.";
        }

        player.sendMessage(
                ChatColor.GRAY + description
        );

        String usage = target.getUsage();
        if (usage != null && !usage.isBlank()) {
            usage = usage.replace("<command>", requested);
            player.sendMessage(
                    ChatColor.YELLOW + "Usage: "
                            + ChatColor.WHITE + usage
            );
        }

        List<String> aliases = target.getAliases().stream()
                .map(this::normalize)
                .filter(alias -> isBypass(player)
                        || isVisible(player, alias))
                .toList();

        if (!aliases.isEmpty()) {
            player.sendMessage(
                    ChatColor.YELLOW + "Aliases: "
                            + ChatColor.WHITE
                            + String.join(", ", aliases)
            );
        }

        return true;
    }

    private String helpDescription(String root, Command command) {
        String custom = HELP_DESCRIPTIONS.get(normalize(root));
        if (custom != null) {
            return custom;
        }
        return command == null ? "" : command.getDescription();
    }

    private void showHelpPage(Player player, int requestedPage) {
        List<String> commands = visibleCommands(player);

        if (commands.isEmpty()) {
            player.sendMessage(
                    ChatColor.GRAY + "No commands are currently available."
            );
            return;
        }

        int pages = Math.max(
                1,
                (commands.size() + helpPageSize - 1)
                        / helpPageSize
        );

        int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * helpPageSize;
        int end = Math.min(start + helpPageSize, commands.size());

        player.sendMessage(
                ChatColor.GOLD + "NubCraft Help "
                        + ChatColor.DARK_GRAY + "(" + page + "/" + pages + ")"
        );

        for (int index = start; index < end; index++) {
            String root = commands.get(index);
            Command command = getServer()
                    .getCommandMap()
                    .getCommand(root);

            String description = helpDescription(root, command);

            String displayRoot = CITIZEN_WORLDEDIT_PERMISSIONS.containsKey(root)
                    ? "//" + root
                    : "/" + root;

            if (description == null || description.isBlank()) {
                player.sendMessage(
                        ChatColor.YELLOW + displayRoot
                );
            } else {
                player.sendMessage(
                        ChatColor.YELLOW + displayRoot
                                + ChatColor.DARK_GRAY + " - "
                                + ChatColor.GRAY + description
                );
            }
        }

        if (pages > 1) {
            player.sendMessage(
                    ChatColor.GRAY + "Use "
                            + ChatColor.WHITE + "/help <page>"
                            + ChatColor.GRAY + " for another page."
            );
        }
    }

    private List<String> visibleCommands(Player player) {
        Set<String> visible = new TreeSet<>();

        for (Map.Entry<String, Command> entry : getServer()
                .getCommandMap()
                .getKnownCommands()
                .entrySet()) {

            String root = normalize(entry.getKey());
            if (root.contains(":")) {
                continue;
            }

            Command command = entry.getValue();
            String canonical = normalize(command.getName());

            if (!root.equals(canonical)
                    && !publicCommands.contains(root)
                    && !permissionOverrides.containsKey(root)) {
                continue;
            }

            if (isBypass(player)
                    || isVisible(player, root)) {
                visible.add(root);
            }
        }

        // WorldEdit's double-slash commands are registered dynamically and
        // are easy for Bukkit command-map filtering to lose. Add the approved
        // Citizen selection commands explicitly when their exact permissions
        // are present.
        if (hasBaseAccess(player)) {
            for (Map.Entry<String, String> entry : CITIZEN_WORLDEDIT_PERMISSIONS.entrySet()) {
                if (player.hasPermission(entry.getValue())) {
                    visible.add(entry.getKey());
                }
            }
        }

        // /help is routed to /nchelp through commands.yml.
        if (isBypass(player) || hasBaseAccess(player)) {
            visible.add("help");
        }

        visible.remove("nchelp");
        return new ArrayList<>(visible);
    }

    private boolean isVisible(Player player, String rawCommand) {
        String root = normalize(rawCommand);

        if (tabHiddenCommands.contains(root)) {
            return false;
        }

        return isAllowed(player, root);
    }

    private boolean isAllowed(Player player, String rawCommand) {
        String root = normalize(rawCommand);

        if (root.isBlank() || root.contains(":")) {
            return false;
        }

        if (hiddenCommands.contains(root)) {
            return false;
        }

        if (!hasBaseAccess(player)) {
            return preCitizenCommands.contains(root);
        }

        if (publicCommands.contains(root)) {
            Command publicCommand = getServer()
                    .getCommandMap()
                    .getCommand(root);

            /*
             * A command being on the public Citizen list means it is eligible
             * to be shown; it does not override the command own permission.
             */
            if (publicCommand == null) {
                return true;
            }

            String publicPermission = publicCommand.getPermission();
            if (publicPermission != null && !publicPermission.isBlank()) {
                return player.hasPermission(publicPermission);
            }

            if (publicCommand instanceof PluginCommand pluginCommand
                    && pluginCommand.getPlugin().getName()
                            .equalsIgnoreCase("Essentials")) {
                return player.hasPermission(
                        "essentials." + normalize(publicCommand.getName())
                );
            }

            return true;
        }

        String override = permissionOverrides.get(root);
        if (override != null) {
            return hasPermissionExpression(player, override);
        }

        Command command = getServer()
                .getCommandMap()
                .getCommand(root);

        if (command == null) {
            return false;
        }

        String permission = command.getPermission();
        if (permission != null && !permission.isBlank()) {
            return player.hasPermission(permission);
        }

        /*
         * EssentialsX performs most permission checks internally instead of
         * declaring them on Bukkit's PluginCommand. Map those commands back
         * to Essentials' normal essentials.<command> permission so staff do
         * not lose legitimate commands merely because we are hiding noise.
         */
        if (command instanceof PluginCommand pluginCommand
                && pluginCommand.getPlugin().getName()
                        .equalsIgnoreCase("Essentials")) {

            return player.hasPermission(
                    "essentials." + normalize(command.getName())
            );
        }

        return false;
    }

    private boolean hasPermissionExpression(
            Player player,
            String expression) {

        for (String permission : expression.split("\\|")) {
            String candidate = permission.trim();
            if (!candidate.isEmpty()
                    && player.hasPermission(candidate)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasBaseAccess(Player player) {
        return basePermission == null
                || basePermission.isBlank()
                || player.hasPermission(basePermission);
    }

    private boolean containsBlockedSubcommand(String[] parts) {
        for (int index = 1; index < parts.length; index++) {
            if (blockedSubcommands.contains(
                    normalize(parts[index]))) {
                return true;
            }
        }
        return false;
    }

    private boolean isBypass(Player player) {
        return player.hasPermission("nccommandcontrol.bypass");
    }

    private String normalize(String input) {
        String normalized = input == null
                ? ""
                : input.trim().toLowerCase(Locale.ROOT);

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return normalized;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)
                || args.length != 1) {
            return List.of();
        }

        String prefix = normalize(args[0]);
        return visibleCommands(player).stream()
                .filter(name -> name.startsWith(prefix))
                .toList();
    }
}
