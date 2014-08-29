/*
 * Copyright (C) 2014 Harry Devane
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mclegends.worldeditforvoting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * https://www.github.com/Harry5573OP
 *
 * @author Harry5573OP
 */
public class WorldEditForVoting extends JavaPlugin implements Listener, CommandExecutor {

      @Getter
      private static WorldEditForVoting plugin_instance = null;
      @Getter
      private PluginDescriptionFile pdf_file = null;

      private String prefix = null;
      private List<String> give_worldedit_commands = new ArrayList<>();
      private ConcurrentHashMap<UUID, Long> player_storage = new ConcurrentHashMap<>();

      @Override
      public void onEnable() {
            plugin_instance = this;
            pdf_file = getDescription();

            logMessage("=[ Plugin version " + getDescription().getVersion() + " starting ]=");

            reload();

            getServer().getPluginManager().registerEvents(this, this);

            //Checker task
            getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
                  public synchronized void run() {
                        for (UUID player_uuid : player_storage.keySet()) {
                              handleCheckWorldEditRemove(player_uuid);
                        }
                        save();
                  }
            }, 100L, 100L);

            logMessage("=[ Plugin version " + getDescription().getVersion() + " started ]=");
      }

      @Override
      public void onDisable() {
            logMessage("=[ Plugin version " + getDescription().getVersion() + " stopping ]=");

            logMessage("=[ Plugin version " + getDescription().getVersion() + " shutdown ]=");
      }

      @Override
      public boolean onCommand(CommandSender sender, Command command, String label, final String[] args) {
            if (!sender.isOp() && !sender.hasPermission("worldeditforvoting.admin")) {
                  sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
                  return true;
            }

            if (args.length != 1) {
                  sender.sendMessage(ChatColor.RED + "Usage: /giveworldedit <username>");
                  return false;
            }

            sender.sendMessage(ChatColor.GREEN + "You have added 24 hours of worldedit to " + args[0]);
            //Do the UUID lookup async.
            getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
                  public synchronized void run() {
                        final UUID player_uuid = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
                        if (player_uuid == null) {
                              return;
                        }

                        getServer().getScheduler().runTask(plugin_instance, new Runnable() {
                              public void run() {
                                    giveWorldEdit(args[0], player_uuid, true);
                              }
                        });
                  }
            });

            return true;
      }

      private void logMessage(String message) {
            getServer().getConsoleSender().sendMessage(ChatColor.GOLD + "[" + getName() + "] " + ChatColor.WHITE + message);
      }

      private void reload() {
            saveDefaultConfig();
            reloadConfig();

            prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix"));

            give_worldedit_commands.clear();
            for (String worldedit_command : getConfig().getStringList("give_worldedit_commands")) {
                  give_worldedit_commands.add(worldedit_command);
                  logMessage("Loaded give_worldedit_command " + worldedit_command);
            }

            this.player_storage.clear();
            for (String user_value : getConfig().getStringList("player_storage")) {
                  String[] split = user_value.split(":");
                  this.player_storage.put(UUID.fromString(split[0]), Long.valueOf(split[1]));
            }
      }

      private void save() {
            List<String> save_values = new ArrayList<>();

            for (Entry<UUID, Long> player_data : this.player_storage.entrySet()) {
                  save_values.add(player_data.getKey().toString() + ":" + player_data.getValue().toString());
            }

            getConfig().set("player_storage", save_values);
            saveConfig();
      }

      @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
      public void onPlayerJoin(final PlayerJoinEvent event) {
            getServer().getScheduler().runTaskLater(this, new Runnable() {
                  public void run() {
                        if (hasPlayerVotedInLast24h(event.getPlayer().getUniqueId())) {
                              giveWorldEdit(event.getPlayer().getName(), event.getPlayer().getUniqueId(), false);
                        }
                  }
            }, 20L);
      }

      private void giveWorldEdit(String player_name, UUID player_uuid, boolean add_to_map) {
            if (add_to_map) {
                  player_storage.put(player_uuid, System.currentTimeMillis());
            }

            for (String worldedit_command : give_worldedit_commands) {
                  getServer().dispatchCommand(Bukkit.getConsoleSender(), worldedit_command.replace("/", "").replace("{username}", player_name));
            }
            if (Bukkit.getPlayer(player_uuid) != null && Bukkit.getPlayer(player_uuid).isOnline()) {
                  Bukkit.getPlayer(player_uuid).sendMessage(prefix + ChatColor.GREEN + "You now have access to worldedit. You will have access for " + ChatColor.UNDERLINE + getHoursOfWorldEditLeft(player_uuid) + ChatColor.RESET + ChatColor.GREEN + " more hours.");
            }
      }

      private void handleCheckWorldEditRemove(UUID player_uuid) {
            if (player_storage.containsKey(player_uuid)) {
                  if (!hasPlayerVotedInLast24h(player_uuid)) {
                        player_storage.remove(player_uuid);

                        if (Bukkit.getPlayer(player_uuid) != null && Bukkit.getPlayer(player_uuid).isOnline()) {
                              Bukkit.getPlayer(player_uuid).sendMessage(prefix + ChatColor.RED + "You no longer have access to WorldEdit, your 24hour access has expired. Vote again with " + ChatColor.UNDERLINE + "/vote" + ChatColor.RESET + ChatColor.RED + " to regain access.");
                        }
                  }
            }
      }

      private boolean hasPlayerVotedInLast24h(UUID player_uuid) {
            if (!player_storage.containsKey(player_uuid)) {
                  return false;
            }

            return getHoursOfWorldEditLeft(player_uuid) <= 0;
      }

      private long getHoursOfWorldEditLeft(UUID player_uuid) {
            return TimeUnit.MILLISECONDS.toHours((player_storage.get(player_uuid) + 86400000) - System.currentTimeMillis());
      }
}
