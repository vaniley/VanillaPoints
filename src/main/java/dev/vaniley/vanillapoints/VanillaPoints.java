package dev.vaniley.vanillapoints;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class VanillaPoints extends JavaPlugin implements Listener, TabCompleter {

    private Location spawnLocation;
    private final Map<UUID, Location> playerHomes = new HashMap<>();
    private final Map<String, Location> warps = new HashMap<>();

    private FileConfiguration messagesConfig;
    private FileConfiguration dataConfig;
    private File dataFile;

    @Override
    public void onEnable() {
        loadDataConfig();
        loadConfigData();
        loadMessagesConfig();

        registerCommand("setspawn", this);
        registerCommand("spawn", this);
        registerCommand("sethome", this);
        registerCommand("home", this);
        registerCommand("setwarp", this);
        registerCommand("warp", this);
        registerCommand("delwarp", this);

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("warp").setTabCompleter(this);
    }

    private void registerCommand(String name, Object listener) {
        Objects.requireNonNull(getCommand(name)).setExecutor((org.bukkit.command.CommandExecutor) listener);
    }

    @Override
    public void onDisable() {
        saveConfigData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getColoredMessage("console-error"));
            return true;
        }

        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "setspawn":
                if (!player.hasPermission("vanillapoints.setspawn")) {
                    player.sendMessage(getColoredMessage("no-permission"));
                    return true;
                }
                setSpawnLocation(player.getLocation());
                player.sendMessage(getColoredMessage("spawn-set"));
                return true;

            case "spawn":
                if (spawnLocation == null) {
                    player.sendMessage(getColoredMessage("spawn-not-set"));
                    return true;
                }
                sendClickableLocationMessage(player, "spawn-location", spawnLocation);
                return true;

            case "sethome":
                setHomeLocation(player);
                player.sendMessage(getColoredMessage("home-set"));
                return true;

            case "home":
                Location homeLocation = getHomeLocation(player);
                if (homeLocation != null) {
                    sendClickableLocationMessage(player, "home-location", homeLocation);
                } else {
                    player.sendMessage(getColoredMessage("home-not-set"));
                }
                return true;

            case "setwarp":
                if (args.length != 1) {
                    player.sendMessage(getColoredMessage("setwarp-usage"));
                    return true;
                }
                setWarpLocation(args[0], player.getLocation());
                player.sendMessage(getColoredMessage("warp-set").replace("{warp}", args[0]));
                return true;

            case "warp":
                if (args.length != 1) {
                    player.sendMessage(getColoredMessage("warp-usage"));
                    return true;
                }
                Location warpLocation = getWarpLocation(args[0]);
                if (warpLocation != null) {
                    sendClickableLocationMessage(player, "warp-location", warpLocation, args[0]);
                } else {
                    player.sendMessage(getColoredMessage("warp-not-set").replace("{warp}", args[0]));
                }
                return true;

            case "delwarp":
                if (args.length != 1) {
                    player.sendMessage(getColoredMessage("delwarp-usage"));
                    return true;
                }
                if (deleteWarpLocation(args[0])) {
                    player.sendMessage(getColoredMessage("warp-deleted").replace("{warp}", args[0]));
                } else {
                    player.sendMessage(getColoredMessage("warp-not-set").replace("{warp}", args[0]));
                }
                return true;

            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("warp") && args.length == 1) {
            return new ArrayList<>(warps.keySet()).stream()
                    .filter(warp -> warp.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void setSpawnLocation(Location location) {
        this.spawnLocation = location.getBlock().getLocation();
    }

    private void setHomeLocation(Player player) {
        playerHomes.put(player.getUniqueId(), player.getLocation().getBlock().getLocation());
    }

    private Location getHomeLocation(Player player) {
        return playerHomes.get(player.getUniqueId());
    }

    private void setWarpLocation(String name, Location location) {
        warps.put(name.toLowerCase(), location.getBlock().getLocation());
    }

    private Location getWarpLocation(String name) {
        return warps.get(name.toLowerCase());
    }

    private boolean deleteWarpLocation(String name) {
        return warps.remove(name.toLowerCase()) != null;
    }

    private void sendClickableLocationMessage(Player player, String messageKey, Location location, String... args) {
        String message = getColoredMessage(messageKey)
                .replace("{x}", String.valueOf(location.getBlockX()))
                .replace("{y}", String.valueOf(location.getBlockY()))
                .replace("{z}", String.valueOf(location.getBlockZ()))
                .replace("{world}", location.getWorld().getName());
        if (args.length > 0) {
            message = message.replace("{warp}", args[0]);
        }

        String coordinatesToCopy = location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();

        TextComponent clickableMessage = new TextComponent(message);
        clickableMessage.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coordinatesToCopy));
        clickableMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(getColoredMessage("coordinates-hover-text"))));

        player.spigot().sendMessage(clickableMessage);
    }

    private void loadDataConfig() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadConfigData() {
        if (dataConfig.contains("spawn")) {
            spawnLocation = getLocationFromConfig(dataConfig, "spawn");
        }
        if (dataConfig.contains("homes")) {
            dataConfig.getConfigurationSection("homes").getKeys(false).forEach(uuidString -> {
                UUID playerUUID = UUID.fromString(uuidString);
                playerHomes.put(playerUUID, getLocationFromConfig(dataConfig, "homes." + uuidString));
            });
        }
        if (dataConfig.contains("warps")) {
            dataConfig.getConfigurationSection("warps").getKeys(false).forEach(warpName -> {
                Location warpLocation = getLocationFromConfig(dataConfig, "warps." + warpName);
                if (warpLocation != null) {
                    warps.put(warpName.toLowerCase(), warpLocation);
                }
            });
        }
    }

    private void saveConfigData() {
        if (spawnLocation != null) {
            saveLocationToConfig(dataConfig, "spawn", spawnLocation);
        }
        playerHomes.forEach((key, value) -> saveLocationToConfig(dataConfig, "homes." + key.toString(), value));
        warps.forEach((key, value) -> saveLocationToConfig(dataConfig, "warps." + key, value));
        try {
            dataConfig.save(dataFile);
        } catch (Exception e) {
            getLogger().severe("Could not save data to data.yml: " + e.getMessage());
        }
    }

    private void saveLocationToConfig(FileConfiguration config, String path, Location location) {
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
    }

    private Location getLocationFromConfig(FileConfiguration config, String path) {
        String worldName = config.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        return new Location(Bukkit.getWorld(worldName), x, y, z);
    }

    private void loadMessagesConfig() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private String getColoredMessage(String key) {
        String message = messagesConfig.getString(key);
        if (message == null) {
            return ChatColor.RED + "Message not found: " + key;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}