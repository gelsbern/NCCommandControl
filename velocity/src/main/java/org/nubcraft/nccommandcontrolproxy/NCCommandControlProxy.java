package org.nubcraft.nccommandcontrolproxy;

import com.google.common.io.ByteArrayDataInput;
import com.google.inject.Inject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

@Plugin(
   id = "nccommandcontrolproxy",
   name = "NCCommandControlProxy",
   version = "0.2.2-SNAPSHOT",
   description = "NubCraft network command-tree filtering",
   authors = {"Nubcraft"}
)
public final class NCCommandControlProxy {
   private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("nubcraft", "commandcontrol");
   private static final Set<String> PUBLIC_COMMANDS = Set.of(
      "help",
      "list",
      "reply",
      "r",
      "ignore",
      "me",
      "afk",
      "ncranks",
      "doc",
      "dhound",
      "bolt",
      "lock",
      "unlock",
      "spawn",
      "home",
      "sethome",
      "channel",
      "ch",
      "tell",
      "w",
      "m",
      "t",
      "pm",
      "message",
      "msg",
      "whisper",
      "sh",
      "y",
      "mch",
      "pvp",
      "voicechat",
      "modreq",
      "modrequest",
      "claim",
      "claimhere",
      "subzone"
   );
   private static final Set<String> CITIZEN_WORLDEDIT_COMMANDS = Set.of(
      "pos1", "pos2", "hpos1", "hpos2", "expand", "contract", "size", "shift", "outset", "inset", "chunk", "sel", "wand"
   );
   private static final Set<String> TAB_HIDDEN_COMMANDS = Set.of(
      "reply",
      "tell",
      "w",
      "m",
      "t",
      "pm",
      "message",
      "whisper",
      "modrequest",
      "doctor",
      "dr",
      "repair",
      "level",
      "nightstalker",
      "night",
      "prospector",
      "instasmelt",
      "stonemason",
      "mushgardener",
      "mush",
      "bricklayer",
      "mastercarpenter",
      "carpenter",
      "carp",
      "mcarpenter",
      "xpert",
      "expert",
      "keepsake",
      "deathhound",
      "ratpack",
      "check-id",
      "checkid",
      "unclaim",
      "complete",
      "tp-id"
   );
   private static final Map<String, String> PERMISSION_COMMANDS = permissionCommands();
   private final ProxyServer server;
   private final Logger logger;
   private final Map<UUID, Set<String>> backendPolicies = new ConcurrentHashMap<>();

   @Inject
   public NCCommandControlProxy(ProxyServer server, Logger logger) {
      this.server = server;
      this.logger = logger;
   }

   @Subscribe
   public void onProxyInitialization(ProxyInitializeEvent event) {
      this.server.getChannelRegistrar().register(new ChannelIdentifier[]{CHANNEL});
      this.logger.info("NCCommandControlProxy registered channel {}", CHANNEL.getId());
   }

   @Subscribe
   public void onPluginMessage(PluginMessageEvent event) {
      if (CHANNEL.equals(event.getIdentifier())) {
         event.setResult(ForwardResult.handled());
         if (event.getSource() instanceof ServerConnection source) {
            try {
               ByteArrayDataInput input = event.dataAsDataStream();
               if (!"VISIBLE".equals(input.readUTF())) {
                  return;
               }

               UUID playerId = UUID.fromString(input.readUTF());
               Player player = source.getPlayer();
               if (!player.getUniqueId().equals(playerId)) {
                  this.logger.warn("Rejected command policy for {} from connection owned by {}", playerId, player.getUniqueId());
                  return;
               }

               int count = input.readInt();
               if (count < 0 || count > 4096) {
                  this.logger.warn("Rejected invalid command policy size {} for {}", count, player.getUsername());
                  return;
               }

               Set<String> commands = new HashSet<>();

               for (int index = 0; index < count; index++) {
                  String command = normalize(input.readUTF());
                  if (!command.isBlank() && !command.contains(":")) {
                     commands.add(command);
                  }
               }

               this.backendPolicies.put(playerId, Set.copyOf(commands));
               this.logger.info("Command policy received for {}: {} command(s)", player.getUsername(), commands.size());
            } catch (Exception var10) {
               this.logger.warn("Could not decode backend command policy", var10);
            }
         }
      }
   }

   @Subscribe
   public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("nccommandcontrol.bypass")) {
         Set<String> allowed = this.policyFor(player);
         int before = event.getRootNode().getChildren().size();
         this.filterAvailableCommandTree(event.getRootNode(), allowed);
         this.logger
            .info(
               "Available command tree for {} filtered {} -> {} (policy={})",
               new Object[]{
                  player.getUsername(),
                  before,
                  event.getRootNode().getChildren().size(),
                  this.backendPolicies.containsKey(player.getUniqueId()) ? "backend" : "local"
               }
            );
      }
   }

   private <S> void filterAvailableCommandTree(RootCommandNode<S> root, Set<String> allowed) {
      for (CommandNode<S> node : new ArrayList<>(root.getChildren())) {
         String rawName = node.getName();
         if (rawName.startsWith("/") && rawName.length() > 1) {
            // Double-slash commands are completed by onTabComplete. Keeping a
            // synthetic slash node here suppresses the vanilla root dropdown.
            root.getChildren().remove(node);
         } else if (!allowed.contains(normalize(rawName))) {
            root.getChildren().remove(node);
         }
      }
   }

   @Subscribe
   public void onTabComplete(TabCompleteEvent event) {
      String partial = event.getPartialMessage();
      if (partial != null && partial.startsWith("/")) {
         Player player = event.getPlayer();
         if (!player.hasPermission("nccommandcontrol.bypass")) {
            Set<String> allowed = this.policyFor(player);
            if (partial.startsWith("//")) {
               String body = partial.substring(2);
               int space = body.indexOf(32);
               String root = normalize(space >= 0 ? body.substring(0, space) : body);
               if (space < 0) {
                  event.getSuggestions().clear();

                  for (String command : CITIZEN_WORLDEDIT_COMMANDS) {
                     if (allowed.contains(command) && command.startsWith(root)) {
                        event.getSuggestions().add("/" + command);
                     }
                  }

                  event.getSuggestions().sort(String.CASE_INSENSITIVE_ORDER);
               } else {
                  if (!CITIZEN_WORLDEDIT_COMMANDS.contains(root) || !allowed.contains(root)) {
                     event.getSuggestions().clear();
                  }
               }
            } else if (!partial.contains(" ")) {
               event.getSuggestions()
                  .removeIf(suggestion -> suggestion.equals("/") ? false : suggestion.startsWith("/") || !allowed.contains(normalize(suggestion)));
               if (partial.equals("/") && CITIZEN_WORLDEDIT_COMMANDS.stream().anyMatch(allowed::contains) && !event.getSuggestions().contains("/")) {
                  event.getSuggestions().add(0, "/");
               }
            }
         }
      }
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.backendPolicies.remove(event.getPlayer().getUniqueId());
   }

   private Set<String> policyFor(Player player) {
      Set<String> backend = this.backendPolicies.get(player.getUniqueId());
      if (backend != null) {
         return backend;
      } else if (!player.hasPermission("nubcraft.citizen")) {
         return Set.of("help");
      } else {
         Set<String> allowed = new HashSet<>(PUBLIC_COMMANDS);

         for (Entry<String, String> entry : PERMISSION_COMMANDS.entrySet()) {
            if (hasPermissionExpression(player, entry.getValue())) {
               allowed.add(entry.getKey());
            }
         }

         allowed.removeAll(TAB_HIDDEN_COMMANDS);
         return allowed;
      }
   }

   private static boolean hasPermissionExpression(Player player, String expression) {
      for (String permission : expression.split("\\|")) {
         String candidate = permission.trim();
         if (!candidate.isEmpty() && player.hasPermission(candidate)) {
            return true;
         }
      }

      return false;
   }

   private static Map<String, String> permissionCommands() {
      Map<String, String> map = new HashMap<>();
      map.put("doctor", "ncranks.service.dr");
      map.put("doc", "ncranks.service.dr");
      map.put("dr", "ncranks.service.dr");
      map.put("repair", "ncranks.service.repairman");
      map.put("rp", "ncranks.service.repairman");
      map.put("level", "ncranks.service.repairman");
      map.put("lvl", "ncranks.service.repairman");
      map.put("nightstalker", "ncranks.mining.nightstalker");
      map.put("night", "ncranks.mining.nightstalker");
      map.put("ns", "ncranks.mining.nightstalker");
      map.put("prospector", "ncranks.mining.ps|ncranks.mining.ps.ore|ncranks.mining.ps.flint|ncranks.mining.ps.logs");
      map.put("ps", "ncranks.mining.ps|ncranks.mining.ps.ore|ncranks.mining.ps.flint|ncranks.mining.ps.logs");
      map.put("instasmelt", "ncranks.mining.instasmelt");
      map.put("smelt", "ncranks.mining.instasmelt");
      map.put("scuba", "ncranks.service.scuba");
      map.put("stonemason", "ncranks.build.stonemason");
      map.put("mason", "ncranks.build.stonemason");
      map.put("mushgardener", "ncranks.build.mushgardener");
      map.put("mush", "ncranks.build.mushgardener");
      map.put("mg", "ncranks.build.mushgardener");
      map.put("bricklayer", "ncranks.build.bricklayer");
      map.put("brick", "ncranks.build.bricklayer");
      map.put("mastercarpenter", "ncranks.build.carpenter");
      map.put("carpenter", "ncranks.build.carpenter");
      map.put("carp", "ncranks.build.carpenter");
      map.put("mcarpenter", "ncranks.build.carpenter");
      map.put("mcarp", "ncranks.build.carpenter");
      map.put("xpert", "ncranks.death.te");
      map.put("expert", "ncranks.death.te");
      map.put("ex", "ncranks.death.te");
      map.put("keepsake", "ncranks.death.ks");
      map.put("ks", "ncranks.death.ks");
      map.put("deathhound", "ncranks.death.hound");
      map.put("dhound", "ncranks.death.hound");
      map.put("respawn", "ncranks.death.respawn");
      map.put("ratpack", "ncranks.death.ratpack");
      map.put("rat", "ncranks.death.ratpack");
      map.put("hat", "ncranks.cosmetic.hat");
      map.put("pos1", "worldedit.selection.pos");
      map.put("pos2", "worldedit.selection.pos");
      map.put("hpos1", "worldedit.selection.hpos");
      map.put("hpos2", "worldedit.selection.hpos");
      map.put("expand", "worldedit.selection.expand");
      map.put("contract", "worldedit.selection.contract");
      map.put("size", "worldedit.selection.size");
      map.put("shift", "worldedit.selection.shift");
      map.put("outset", "worldedit.selection.outset");
      map.put("inset", "worldedit.selection.inset");
      map.put("chunk", "worldedit.selection.chunk");
      map.put("sel", "worldedit.analysis.sel");
      map.put("wand", "worldedit.wand");
      map.put("tg", "chatcontrol.channel.send.staff");
      map.put("ts", "chatcontrol.channel.send.seniormoderator");
      map.put("tm", "chatcontrol.channel.send.moderator");
      map.put("ta", "chatcontrol.channel.send.admin");
      map.put("sa", "chatcontrol.channel.send.senioradmin");
      map.put("bring", "essentials.tphere");
      map.put("login", "pv.login");
      map.put("logout", "pv.logout");
      map.put("gamemode", "essentials.gamemode");
      map.put("god", "essentials.god");
      map.put("check", "modtrs.command.check");
      map.put("dibs", "modtrs.command.complete");
      map.put("backsies", "modtrs.command.complete");
      map.put("done", "modtrs.command.complete");
      map.put("reopen", "modtrs.command.complete");
      map.put("hold", "modtrs.command.complete");
      map.put("tpid", "modtrs.command.teleport");
      map.put("modreq-ban", "modtrs.command.ban");
      map.put("modreq-unban", "modtrs.command.unban");
      map.put("modtrs", "modtrs.admin");
      return Map.copyOf(map);
   }

   private static String normalize(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);

      while (normalized.startsWith("/")) {
         normalized = normalized.substring(1);
      }

      return normalized;
   }
}
