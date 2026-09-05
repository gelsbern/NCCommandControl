package org.nubcraft.nccommandcontrolproxy;

import com.google.common.io.ByteArrayDataInput;
import com.google.inject.Inject;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

@Plugin(
   id = "nccommandcontrolproxy",
   name = "NCCommandControlProxy",
   version = "0.1.0-SNAPSHOT",
   description = "NubCraft network command-tree filtering",
   authors = {"Nubcraft"}
)
public final class NCCommandControlProxy {
   private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("nubcraft", "commandcontrol");
   private final ProxyServer server;
   private final Logger logger;
   private final Map<UUID, Set<String>> allowedCommands = new ConcurrentHashMap<>();

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

               this.allowedCommands.put(playerId, Set.copyOf(commands));
            } catch (Exception var10) {
               this.logger.warn("Could not decode backend command policy", var10);
            }
         }
      }
   }

   @Subscribe
   public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
      Set<String> allowed = this.allowedCommands.get(event.getPlayer().getUniqueId());
      event.getRootNode().getChildren().removeIf(node -> {
         String name = normalize(node.getName());
         return allowed == null || !allowed.contains(name);
      });
   }

   @Subscribe
   public void onTabComplete(TabCompleteEvent event) {
      String partial = event.getPartialMessage();
      if (partial != null && partial.startsWith("/") && !partial.contains(" ")) {
         Set<String> allowed = this.allowedCommands.get(event.getPlayer().getUniqueId());
         event.getSuggestions().removeIf(suggestion -> {
            String name = normalize(suggestion);
            return allowed == null || !allowed.contains(name);
         });
      }
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.allowedCommands.remove(event.getPlayer().getUniqueId());
   }

   private static String normalize(String value) {
      String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);

      while (normalized.startsWith("/")) {
         normalized = normalized.substring(1);
      }

      return normalized;
   }
}
