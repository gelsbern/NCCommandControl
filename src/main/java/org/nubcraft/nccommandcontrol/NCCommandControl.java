package org.nubcraft.nccommandcontrol;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
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

public final class NCCommandControl extends JavaPlugin implements Listener, TabCompleter {
   private static final String PROXY_CHANNEL = "nubcraft:commandcontrol";
   private static final Set<String> PLAYER_TARGET_FIRST = Set.of(
      "tell", "w", "m", "t", "pm", "message", "msg", "whisper", "ignore", "bring", "god", "modreq-ban", "modreq-unban"
   );
   private final Set<String> publicCommands = new HashSet<>();
   private final Set<String> preCitizenCommands = new HashSet<>();
   private final Set<String> hiddenCommands = new HashSet<>();
   private final Set<String> blockedSubcommands = new HashSet<>();
   private final Map<String, String> permissionOverrides = new HashMap<>();
   private final Map<UUID, Set<String>> publishedCommands = new ConcurrentHashMap<>();
   private final Map<UUID, Set<String>> visiblePlayerNames = new ConcurrentHashMap<>();
   private String basePermission;
   private int helpPageSize;
   private BukkitTask refreshTask;

   public void onEnable() {
      this.saveDefaultConfig();
      this.reloadPolicy();
      this.getServer().getPluginManager().registerEvents(this, this);
      this.getServer().getMessenger().registerOutgoingPluginChannel(this, "nubcraft:commandcontrol");
      PluginCommand help = this.getCommand("nchelp");
      if (help == null) {
         throw new IllegalStateException("nchelp is missing from plugin.yml");
      } else {
         help.setExecutor(this::executeHelp);
         help.setTabCompleter(this);

         for (Player player : this.getServer().getOnlinePlayers()) {
            this.publishCommands(player, true);
            player.updateCommands();
         }

         this.refreshTask = this.getServer().getScheduler().runTaskTimer(this, this::refreshPublishedCommands, 40L, 40L);
         this.getLogger().info("Permission-aware command visibility enabled.");
      }
   }

   public void onDisable() {
      if (this.refreshTask != null) {
         this.refreshTask.cancel();
         this.refreshTask = null;
      }

      this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, "nubcraft:commandcontrol");
      this.publishedCommands.clear();
      this.visiblePlayerNames.clear();
   }

   private void reloadPolicy() {
      this.reloadConfig();
      this.basePermission = this.getConfig().getString("base-player-permission", "nubcraft.citizen");
      this.helpPageSize = Math.max(5, this.getConfig().getInt("help-page-size", 10));
      this.loadSet("public-commands", this.publicCommands);
      this.loadSet("pre-citizen-commands", this.preCitizenCommands);
      this.loadSet("hidden-commands", this.hiddenCommands);
      this.loadSet("blocked-subcommands", this.blockedSubcommands);
      this.permissionOverrides.clear();

      for (String key : this.getConfig().getConfigurationSection("permission-overrides") == null
         ? Set.<String>of()
         : this.getConfig().getConfigurationSection("permission-overrides").getKeys(false)) {
         String permission = this.getConfig().getString("permission-overrides." + key, "");
         if (!permission.isBlank()) {
            this.permissionOverrides.put(this.normalize(key), permission.trim());
         }
      }
   }

   private void loadSet(String path, Set<String> target) {
      target.clear();

      for (String value : this.getConfig().getStringList(path)) {
         target.add(this.normalize(value));
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.getServer().getScheduler().runTask(this, () -> {
         this.publishCommands(event.getPlayer(), true);
         event.getPlayer().updateCommands();
      });
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.publishedCommands.remove(event.getPlayer().getUniqueId());
      this.visiblePlayerNames.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onCommandList(PlayerCommandSendEvent event) {
      Player player = event.getPlayer();
      if (!this.isBypass(player)) {
         event.getCommands().removeIf(command -> !this.isVisible(player, command));
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onBrigadierCommandList(AsyncPlayerSendCommandsEvent<?> event) {
      Player player = event.getPlayer();
      if (!this.isBypass(player)) {
         event.getCommandNode().getChildren().removeIf(node -> !this.isVisible(player, node.getName()));
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      if (!this.isBypass(player)) {
         String message = event.getMessage();
         if (message.length() > 1) {
            String[] parts = message.substring(1).trim().split("\\s+");
            if (parts.length != 0) {
               String root = this.normalize(parts[0]);
               if (!this.isVisible(player, root) || this.containsBlockedSubcommand(parts)) {
                  event.setCancelled(true);
                  player.sendMessage(ChatColor.RED + "Unknown command.");
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
      if (event.getSender() instanceof Player player && !this.isBypass(player) && event.isCommand()) {
         String buffer = event.getBuffer();
         String stripped = buffer.startsWith("/") ? buffer.substring(1) : buffer;
         String[] parts = stripped.split(" +", -1);
         Set<String> visible = this.publishedCommands.get(player.getUniqueId());
         if (visible != null) {
            if (parts.length == 1) {
               String prefix = this.normalize(parts[0]);
               event.setCompletions(visible.stream().filter(name -> name.startsWith(prefix)).sorted().toList());
               event.setHandled(true);
            } else if (parts.length >= 2 && this.normalize(parts[0]).equals("help")) {
               String prefix = this.normalize(parts[parts.length - 1]);
               event.setCompletions(visible.stream().filter(name -> name.startsWith(prefix)).sorted().toList());
               event.setHandled(true);
            } else {
               String root = this.normalize(parts[0]);
               boolean playerArgument = PLAYER_TARGET_FIRST.contains(root) && parts.length == 2;
               boolean gamemodePlayerArgument = root.equals("gamemode") && parts.length == 3;
               if (playerArgument || gamemodePlayerArgument) {
                  String prefix = parts[parts.length - 1].toLowerCase(Locale.ROOT);
                  Set<String> names = this.visiblePlayerNames.getOrDefault(player.getUniqueId(), Set.of());
                  event.setCompletions(
                     names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).toList()
                  );
                  event.setHandled(true);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onTabComplete(TabCompleteEvent event) {
      if (event.getSender() instanceof Player player) {
         if (!this.isBypass(player)) {
            String buffer = event.getBuffer();
            String stripped = buffer.startsWith("/") ? buffer.substring(1) : buffer;
            String[] parts = stripped.split("\\s+", -1);
            if (parts.length == 1) {
               String prefix = this.normalize(parts[0]);
               event.setCompletions(this.visibleCommands(player).stream().filter(name -> name.startsWith(prefix)).toList());
            } else if (parts.length >= 2 && this.normalize(parts[0]).equals("help")) {
               String prefix = this.normalize(parts[parts.length - 1]);
               event.setCompletions(this.visibleCommands(player).stream().filter(name -> name.startsWith(prefix)).toList());
            } else {
               List<String> filtered = new ArrayList<>();

               for (String completion : event.getCompletions()) {
                  String normalized = this.normalize(completion);
                  if (!this.blockedSubcommands.contains(normalized)) {
                     Player target = Bukkit.getPlayerExact(completion);
                     if (target == null || player.canSee(target)) {
                        filtered.add(completion);
                     }
                  }
               }

               event.setCompletions(filtered);
            }
         }
      }
   }

   private void cacheVisiblePlayers(Player player) {
      Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      for (Player target : this.getServer().getOnlinePlayers()) {
         if (player.canSee(target)) {
            names.add(target.getName());
         }
      }

      this.visiblePlayerNames.put(player.getUniqueId(), Set.copyOf(names));
   }

   private void refreshPublishedCommands() {
      for (Player player : this.getServer().getOnlinePlayers()) {
         this.publishCommands(player, false);
      }
   }

   private void publishCommands(Player player, boolean force) {
      this.cacheVisiblePlayers(player);
      Set<String> visible = new TreeSet<>(this.visibleCommands(player));
      Set<String> previous = this.publishedCommands.get(player.getUniqueId());
      if (force || !visible.equals(previous)) {
         try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);

            try {
               output.writeUTF("VISIBLE");
               output.writeUTF(player.getUniqueId().toString());
               output.writeInt(visible.size());

               for (String command : visible) {
                  output.writeUTF(command);
               }
            } catch (Throwable var10) {
               try {
                  output.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }

               throw var10;
            }

            output.close();
            player.sendPluginMessage(this, "nubcraft:commandcontrol", bytes.toByteArray());
            this.publishedCommands.put(player.getUniqueId(), Set.copyOf(visible));
            if (!force) {
               player.updateCommands();
            }
         } catch (IOException var11) {
            this.getLogger().warning("Could not publish command policy for " + player.getName() + ": " + var11.getMessage());
         }
      }
   }

   private boolean executeHelp(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("Player help is available in game.");
         return true;
      } else if (!this.isBypass(player) && !this.hasBaseAccess(player)) {
         player.sendMessage(ChatColor.RED + "Unknown command.");
         return true;
      } else if (args.length == 0) {
         this.showHelpPage(player, 1);
         return true;
      } else {
         if (args.length == 1) {
            try {
               int page = Integer.parseInt(args[0]);
               this.showHelpPage(player, page);
               return true;
            } catch (NumberFormatException var11) {
            }
         }

         String requested = this.normalize(args[0]);
         if (!this.isBypass(player) && !this.isVisible(player, requested)) {
            player.sendMessage(ChatColor.RED + "Unknown command.");
            return true;
         } else {
            Command target = this.getServer().getCommandMap().getCommand(requested);
            if (target == null && requested.equals("help")) {
               this.showHelpPage(player, 1);
               return true;
            } else if (target == null) {
               player.sendMessage(ChatColor.RED + "Unknown command.");
               return true;
            } else {
               player.sendMessage(ChatColor.GOLD + "/" + requested);
               String description = target.getDescription();
               if (description == null || description.isBlank()) {
                  description = "Available command.";
               }

               player.sendMessage(ChatColor.GRAY + description);
               String usage = target.getUsage();
               if (usage != null && !usage.isBlank()) {
                  usage = usage.replace("<command>", requested);
                  player.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + usage);
               }

               List<String> aliases = target.getAliases()
                  .stream()
                  .map(this::normalize)
                  .filter(alias -> this.isBypass(player) || this.isVisible(player, alias))
                  .toList();
               if (!aliases.isEmpty()) {
                  player.sendMessage(ChatColor.YELLOW + "Aliases: " + ChatColor.WHITE + String.join(", ", aliases));
               }

               return true;
            }
         }
      }
   }

   private void showHelpPage(Player player, int requestedPage) {
      List<String> commands = this.visibleCommands(player);
      if (commands.isEmpty()) {
         player.sendMessage(ChatColor.GRAY + "No commands are currently available.");
      } else {
         int pages = Math.max(1, (commands.size() + this.helpPageSize - 1) / this.helpPageSize);
         int page = Math.max(1, Math.min(requestedPage, pages));
         int start = (page - 1) * this.helpPageSize;
         int end = Math.min(start + this.helpPageSize, commands.size());
         player.sendMessage(ChatColor.GOLD + "NubCraft Help " + ChatColor.DARK_GRAY + "(" + page + "/" + pages + ")");

         for (int index = start; index < end; index++) {
            String root = commands.get(index);
            Command command = this.getServer().getCommandMap().getCommand(root);
            String description = command == null ? "" : command.getDescription();
            if (description != null && !description.isBlank()) {
               player.sendMessage(ChatColor.YELLOW + "/" + root + ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + description);
            } else {
               player.sendMessage(ChatColor.YELLOW + "/" + root);
            }
         }

         if (pages > 1) {
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/help <page>" + ChatColor.GRAY + " for another page.");
         }
      }
   }

   private List<String> visibleCommands(Player player) {
      Set<String> visible = new TreeSet<>();

      for (Entry<String, Command> entry : this.getServer().getCommandMap().getKnownCommands().entrySet()) {
         String root = this.normalize(entry.getKey());
         if (!root.contains(":")) {
            Command command = entry.getValue();
            String canonical = this.normalize(command.getName());
            if ((root.equals(canonical) || this.publicCommands.contains(root) || this.permissionOverrides.containsKey(root))
               && (this.isBypass(player) || this.isVisible(player, root))) {
               visible.add(root);
            }
         }
      }

      if (this.isBypass(player) || this.hasBaseAccess(player)) {
         visible.add("help");
      }

      visible.remove("nchelp");
      return new ArrayList<>(visible);
   }

   private boolean isVisible(Player player, String rawCommand) {
      String root = this.normalize(rawCommand);
      if (root.isBlank() || root.contains(":")) {
         return false;
      } else if (this.hiddenCommands.contains(root)) {
         return false;
      } else if (!this.hasBaseAccess(player)) {
         return this.preCitizenCommands.contains(root);
      } else if (this.publicCommands.contains(root)) {
         return true;
      } else {
         String override = this.permissionOverrides.get(root);
         if (override != null) {
            return this.hasPermissionExpression(player, override);
         } else {
            Command command = this.getServer().getCommandMap().getCommand(root);
            if (command == null) {
               return false;
            } else {
               String permission = command.getPermission();
               if (permission != null && !permission.isBlank()) {
                  return player.hasPermission(permission);
               } else {
                  return command instanceof PluginCommand pluginCommand && pluginCommand.getPlugin().getName().equalsIgnoreCase("Essentials")
                     ? player.hasPermission("essentials." + this.normalize(command.getName()))
                     : false;
               }
            }
         }
      }
   }

   private boolean hasPermissionExpression(Player player, String expression) {
      for (String permission : expression.split("\\|")) {
         String candidate = permission.trim();
         if (!candidate.isEmpty() && player.hasPermission(candidate)) {
            return true;
         }
      }

      return false;
   }

   private boolean hasBaseAccess(Player player) {
      return this.basePermission == null || this.basePermission.isBlank() || player.hasPermission(this.basePermission);
   }

   private boolean containsBlockedSubcommand(String[] parts) {
      for (int index = 1; index < parts.length; index++) {
         if (this.blockedSubcommands.contains(this.normalize(parts[index]))) {
            return true;
         }
      }

      return false;
   }

   private boolean isBypass(Player player) {
      return player.hasPermission("nccommandcontrol.bypass");
   }

   private String normalize(String input) {
      String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);

      while (normalized.startsWith("/")) {
         normalized = normalized.substring(1);
      }

      return normalized;
   }

   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      if (sender instanceof Player player && args.length == 1) {
         String prefix = this.normalize(args[0]);
         return this.visibleCommands(player).stream().filter(name -> name.startsWith(prefix)).toList();
      } else {
         return List.of();
      }
   }
}
