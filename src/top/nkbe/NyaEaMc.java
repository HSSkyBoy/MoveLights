package top.nkbe;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NyaEaMc extends JavaPlugin implements Listener {

    private static final String BOATFLY_CHANNEL = "boatfly:speed";

    private LightManager lightManager;
    private NoteManager noteManager;
    private NickManager nickManager;
    private Lang lang;
    private EmojiPackServer emojiPackServer;
    private ChatListener chatListener;
    private TpaManager tpaManager;
    private Double globalBoatSpeed;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.lang = new Lang(this);
        this.noteManager = new NoteManager(this);
        this.nickManager = new NickManager(this);
        this.tpaManager = new TpaManager(this, this.lang);
        double savedBoatSpeed = getConfig().getDouble("boatfly.global-speed", -1.0D);
        this.globalBoatSpeed = savedBoatSpeed >= 0.1D && savedBoatSpeed <= 50.0D ? savedBoatSpeed : null;
        getServer().getMessenger().registerOutgoingPluginChannel(this, BOATFLY_CHANNEL);
        getCommand("tpa").setExecutor(this);
        getCommand("tpa").setTabCompleter(this);
        getCommand("tpahere").setExecutor(this);
        getCommand("tpahere").setTabCompleter(this);
        getCommand("tpaccept").setExecutor(this);
        getCommand("tpaccept").setTabCompleter(this);
        getCommand("tpdeny").setExecutor(this);
        getCommand("tpdeny").setTabCompleter(this);
        getCommand("tpcancel").setExecutor(this);
        getCommand("tpcancel").setTabCompleter(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        registerChatListener();

        startEmojiPackServer();
        startLightTask();

        // bStats
        int pluginId = 32054;
        Metrics metrics = new Metrics(this, pluginId);

        // Optional: Add custom charts
        metrics.addCustomChart(
            new Metrics.SimplePie("chart_id", () -> "My value")
        );

        this.lang.send(Bukkit.getConsoleSender(), "compat-version");
        this.lang.send(Bukkit.getConsoleSender(), "enable-success");
    }

    @Override
    public void onDisable() {
        this.lang.send(Bukkit.getConsoleSender(), "disable-start");

        if (this.lightManager != null) {
            this.lightManager.removeAllPlayerLight();
            this.lightManager.cancel();
        }
        if (this.emojiPackServer != null) {
            this.emojiPackServer.stop();
            this.emojiPackServer = null;
        }
        if (this.tpaManager != null) this.tpaManager.shutdown();

        this.lang.send(Bukkit.getConsoleSender(), "disable-success");
    }

    private void registerChatListener() {
        if (this.chatListener != null) {
            HandlerList.unregisterAll(this.chatListener);
        }
        this.chatListener = new ChatListener(this);
        Bukkit.getPluginManager().registerEvents(this.chatListener, this);
    }

    // 啟動內建 emoji 資源包伺服器（供 Java 端玩家下載）
    private void startEmojiPackServer() {
        if (this.emojiPackServer != null) {
            this.emojiPackServer.stop();
            this.emojiPackServer = null;
        }
        if (!getConfig().getBoolean("emoji-pack.enable", true)) return;

        try (InputStream in = getResource("resourcepack.zip")) {
            if (in == null) {
                getLogger().warning("找不到內建資源包 resourcepack.zip");
                return;
            }
            byte[] pack = readAll(in);
            this.emojiPackServer = new EmojiPackServer(this, pack,
                    getConfig().getInt("emoji-pack.port", 8399),
                    getConfig().getString("emoji-pack.host", ""));
            this.emojiPackServer.start();
            getLogger().info("emoji 資源包伺服器: " + this.emojiPackServer.getUrl());
        } catch (IOException e) {
            getLogger().warning("無法啟動 emoji 資源包伺服器: " + e.getMessage());
            this.emojiPackServer = null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.nickManager.apply(event.getPlayer());
        if (this.emojiPackServer != null) {
            event.getPlayer().setResourcePack(this.emojiPackServer.getUrl(), this.emojiPackServer.getHash());
        }
        String joinMessage = formatServerMessage("chat.join-message", event.getPlayer().getDisplayName());
        if (joinMessage != null) event.setJoinMessage(joinMessage);
        if (this.globalBoatSpeed != null) {
            Bukkit.getScheduler().runTaskLater(this,
                    () -> sendBoatSpeed(event.getPlayer(), this.globalBoatSpeed), 20L);
        }
    }

    //定時重啟
    private void startLightTask() {
        if (this.lightManager != null) {
            this.lightManager.cancel();
        }
        this.lightManager = new LightManager(this);
        long refreshTicks = getConfig().getInt("refresh", 2);
        this.lightManager.runTaskTimer(this, 0, refreshTicks);
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent e) {
        if (this.lightManager != null) {
            // 玩家退出時清除狀態
            this.lightManager.removePlayerLight(e.getPlayer());
        }
        if (this.tpaManager != null) this.tpaManager.handleQuit(e.getPlayer());
        String quitMessage = formatServerMessage("chat.quit-message", e.getPlayer().getDisplayName());
        if (quitMessage != null) e.setQuitMessage(quitMessage);
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && this.tpaManager != null) this.tpaManager.cancelWarmupBySneak(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (this.tpaManager != null) this.tpaManager.handleDeath(event.getEntity());
    }

    private String formatServerMessage(String path, String playerName) {
        String message = getConfig().getString(path, "");
        if (message.isEmpty()) return null;
        return ChatColor.translateAlternateColorCodes('&', message.replace("{0}", playerName));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("nyaea")) return tpaCommand(sender, command, args);
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            this.showHelp(sender);
        } else if (args[0].equalsIgnoreCase("reload")) {
            this.reload(sender);
        } else if (args[0].equalsIgnoreCase("toggle")) {
            this.toggle(sender);
        } else if (args[0].equalsIgnoreCase("note")) {
            this.noteCommand(sender, args);
        } else if (args[0].equalsIgnoreCase("nick")) {
            nickCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("realname")) {
            realNameCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("ping")) {
            pingCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("broadcast")) {
            broadcastCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("heal")) {
            healCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("feed")) {
            feedCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("fly")) {
            flyCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("speed")) {
            speedCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("clearinventory")) {
            clearInventoryCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("gamemode")) {
            gameModeCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("weather")) {
            weatherCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("time")) {
            timeCommand(sender, sliceArgs(args));
        } else if (args[0].equalsIgnoreCase("invsee")) {
            invSeeCommand(sender, sliceArgs(args), false);
        } else if (args[0].equalsIgnoreCase("endersee")) {
            invSeeCommand(sender, sliceArgs(args), true);
        } else if (args[0].equalsIgnoreCase("boatspeed")) {
            boatSpeedCommand(sender, sliceArgs(args));
        }
        return true;
    }

    private static String[] sliceArgs(String[] args) {
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }

    private void nickCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            this.lang.send(sender, "player-only");
            return;
        }
        Player target = (Player) sender;
        String nickname;
        if (args.length == 1) {
            nickname = args[0];
        } else if (args.length == 2 && sender.hasPermission("nyaeamc.nick.others")) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                this.lang.send(sender, "player-notfound", args[0]);
                return;
            }
            nickname = args[1];
        } else {
            this.lang.send(sender, "nick-usage");
            return;
        }

        if (target.equals(sender) && !sender.hasPermission("nyaeamc.nick")) {
            this.lang.send(sender, "no-permission");
            return;
        }
        if (!target.equals(sender) && !sender.hasPermission("nyaeamc.nick.others")) {
            this.lang.send(sender, "no-permission");
            return;
        }

        if (nickname.equalsIgnoreCase("off")) {
            if (this.nickManager.clearNickname(target)) {
                this.lang.send(sender, "nick-cleared", target.getName());
            } else {
                this.lang.send(sender, "nick-not-set", target.getName());
            }
            return;
        }

        boolean usesColorCode = nickname.indexOf('&') >= 0 || nickname.indexOf('§') >= 0;
        if (usesColorCode && !sender.hasPermission("nyaeamc.nick.color")) {
            this.lang.send(sender, "nick-color-no-permission");
            return;
        }
        String coloredNickname = ChatColor.translateAlternateColorCodes('&', nickname);
        String visibleNickname = ChatColor.stripColor(coloredNickname);
        if (visibleNickname == null || visibleNickname.isEmpty() || visibleNickname.length() > 16) {
            this.lang.send(sender, "nick-invalid");
            return;
        }

        this.nickManager.setNickname(target, nickname);
        this.lang.send(sender, "nick-set", target.getName(), coloredNickname);
        if (!target.equals(sender)) this.lang.send(target, "nick-set-by-other", coloredNickname, sender.getName());
    }

    private void realNameCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.nick")) {
            this.lang.send(sender, "no-permission");
            return;
        }
        if (args.length != 1) {
            this.lang.send(sender, "realname-usage");
            return;
        }
        String name = this.nickManager.getRealName(args[0]);
        if (name == null) {
            this.lang.send(sender, "realname-notfound", args[0]);
            return;
        }
        this.lang.send(sender, "realname-result", args[0], name);
    }

    private void pingCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.ping")) {
            this.lang.send(sender, "no-permission");
            return;
        }
        Player target;
        if (args.length == 0 && sender instanceof Player) {
            target = (Player) sender;
        } else if (args.length == 1 && sender.hasPermission("nyaeamc.ping.others")) {
            target = Bukkit.getPlayer(args[0]);
        } else {
            this.lang.send(sender, "ping-usage");
            return;
        }
        if (target == null) {
            this.lang.send(sender, "player-notfound", args.length == 0 ? "" : args[0]);
            return;
        }
        this.lang.send(sender, "ping-result", target.getName(), target.getPing());
    }

    private void broadcastCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.broadcast")) {
            this.lang.send(sender, "no-permission");
            return;
        }
        if (args.length == 0) {
            this.lang.send(sender, "broadcast-usage");
            return;
        }
        String message = String.join(" ", args);
        if (sender.hasPermission("nyaeamc.chat.color")) {
            message = ChatColor.translateAlternateColorCodes('&', message);
        }
        Bukkit.broadcastMessage(this.lang.get("broadcast-format", message));
    }

    private Player findTarget(CommandSender sender, String[] args, String usageKey) {
        if (args.length == 0 && sender instanceof Player) return (Player) sender;
        if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) return target;
            this.lang.send(sender, "player-notfound", args[0]);
            return null;
        }
        this.lang.send(sender, usageKey);
        return null;
    }

    private void healCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.heal")) { this.lang.send(sender, "no-permission"); return; }
        Player target = findTarget(sender, args, "heal-usage");
        if (target == null) return;
        target.setHealth(target.getMaxHealth());
        target.setFireTicks(0);
        this.lang.send(sender, "heal-success", target.getName());
        if (!target.equals(sender)) this.lang.send(target, "healed-by", sender.getName());
    }

    private void feedCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.feed")) { this.lang.send(sender, "no-permission"); return; }
        Player target = findTarget(sender, args, "feed-usage");
        if (target == null) return;
        target.setFoodLevel(20);
        target.setSaturation(20.0F);
        this.lang.send(sender, "feed-success", target.getName());
        if (!target.equals(sender)) this.lang.send(target, "fed-by", sender.getName());
    }

    private void flyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.fly")) { this.lang.send(sender, "no-permission"); return; }
        Player target = findTarget(sender, args, "fly-usage");
        if (target == null) return;
        target.setAllowFlight(!target.getAllowFlight());
        if (!target.getAllowFlight() && target.isFlying()) target.setFlying(false);
        this.lang.send(sender, target.getAllowFlight() ? "fly-enabled" : "fly-disabled", target.getName());
    }

    private void speedCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { this.lang.send(sender, "player-only"); return; }
        if (!sender.hasPermission("nyaeamc.speed")) { this.lang.send(sender, "no-permission"); return; }
        String mode = "walk";
        String value;
        if (args.length == 1) value = args[0];
        else if (args.length == 2 && (args[0].equalsIgnoreCase("walk") || args[0].equalsIgnoreCase("fly"))) {
            mode = args[0].toLowerCase();
            value = args[1];
        } else { this.lang.send(sender, "speed-usage"); return; }
        try {
            float speed = Float.parseFloat(value);
            if (speed < 1.0F || speed > 10.0F) { this.lang.send(sender, "speed-range"); return; }
            if (mode.equals("fly")) ((Player) sender).setFlySpeed(speed / 10.0F);
            else ((Player) sender).setWalkSpeed(speed / 10.0F);
            this.lang.send(sender, "speed-success", mode, String.valueOf(speed));
        } catch (IllegalArgumentException exception) {
            this.lang.send(sender, "speed-usage");
        }
    }

    private void clearInventoryCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.clearinventory")) { this.lang.send(sender, "no-permission"); return; }
        Player target = findTarget(sender, args, "clearinventory-usage");
        if (target == null) return;
        target.getInventory().clear();
        this.lang.send(sender, "clearinventory-success", target.getName());
    }

    private void gameModeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.gamemode")) { this.lang.send(sender, "no-permission"); return; }
        if (args.length < 1 || args.length > 2) { this.lang.send(sender, "gamemode-usage"); return; }
        GameMode mode = parseGameMode(args[0]);
        if (mode == null) { this.lang.send(sender, "gamemode-invalid"); return; }
        Player target = findTarget(sender, args.length == 2 ? new String[]{args[1]} : new String[0], "gamemode-usage");
        if (target == null) return;
        target.setGameMode(mode);
        this.lang.send(sender, "gamemode-success", target.getName(), mode.name().toLowerCase());
        if (!target.equals(sender)) this.lang.send(target, "gamemode-set-by", mode.name().toLowerCase(), sender.getName());
    }

    private GameMode parseGameMode(String value) {
        if (value.equalsIgnoreCase("survival") || value.equalsIgnoreCase("s") || value.equals("0")) return GameMode.SURVIVAL;
        if (value.equalsIgnoreCase("creative") || value.equalsIgnoreCase("c") || value.equals("1")) return GameMode.CREATIVE;
        if (value.equalsIgnoreCase("adventure") || value.equalsIgnoreCase("a") || value.equals("2")) return GameMode.ADVENTURE;
        if (value.equalsIgnoreCase("spectator") || value.equalsIgnoreCase("sp") || value.equals("3")) return GameMode.SPECTATOR;
        return null;
    }

    private void weatherCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.weather")) { this.lang.send(sender, "no-permission"); return; }
        if (args.length < 1 || args.length > 2) { this.lang.send(sender, "weather-usage"); return; }
        World world = findWorld(sender, args.length == 2 ? args[1] : null, "weather-usage");
        if (world == null) return;
        if (args[0].equalsIgnoreCase("clear")) {
            world.setStorm(false);
            world.setThundering(false);
        } else if (args[0].equalsIgnoreCase("rain")) {
            world.setStorm(true);
            world.setThundering(false);
        } else if (args[0].equalsIgnoreCase("thunder")) {
            world.setStorm(true);
            world.setThundering(true);
        } else { this.lang.send(sender, "weather-usage"); return; }
        this.lang.send(sender, "weather-success", args[0].toLowerCase(), world.getName());
    }

    private void timeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.time")) { this.lang.send(sender, "no-permission"); return; }
        if (args.length < 1 || args.length > 2) { this.lang.send(sender, "time-usage"); return; }
        World world = findWorld(sender, args.length == 2 ? args[1] : null, "time-usage");
        if (world == null) return;
        long time;
        if (args[0].equalsIgnoreCase("day")) time = 1000L;
        else if (args[0].equalsIgnoreCase("night")) time = 13000L;
        else {
            try { time = Long.parseLong(args[0]); }
            catch (NumberFormatException exception) { this.lang.send(sender, "time-usage"); return; }
            if (time < 0L || time > 24000L) { this.lang.send(sender, "time-range"); return; }
        }
        world.setTime(time);
        this.lang.send(sender, "time-success", String.valueOf(time), world.getName());
    }

    private World findWorld(CommandSender sender, String worldName, String usageKey) {
        if (worldName == null) {
            if (sender instanceof Player) return ((Player) sender).getWorld();
            this.lang.send(sender, usageKey);
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) this.lang.send(sender, "world-notfound", worldName);
        return world;
    }

    private void invSeeCommand(CommandSender sender, String[] args, boolean enderChest) {
        if (!sender.hasPermission(enderChest ? "nyaeamc.endersee" : "nyaeamc.invsee")) { this.lang.send(sender, "no-permission"); return; }
        if (!(sender instanceof Player)) { this.lang.send(sender, "player-only"); return; }
        if (args.length != 1) { this.lang.send(sender, enderChest ? "endersee-usage" : "invsee-usage"); return; }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { this.lang.send(sender, "player-notfound", args[0]); return; }
        ((Player) sender).openInventory(enderChest ? target.getEnderChest() : target.getInventory());
        this.lang.send(sender, enderChest ? "endersee-success" : "invsee-success", target.getName());
    }

    private void boatSpeedCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.boatspeed")) { this.lang.send(sender, "no-permission"); return; }
        if (args.length != 1) { this.lang.send(sender, "boatspeed-usage"); return; }
        try {
            double speed = Double.parseDouble(args[0]);
            if (speed < 0.1D || speed > 50.0D) { this.lang.send(sender, "boatspeed-range"); return; }
            this.globalBoatSpeed = speed;
            getConfig().set("boatfly.global-speed", speed);
            saveConfig();
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (sendBoatSpeed(player, speed)) count++;
            }
            this.lang.send(sender, "boatspeed-success", String.format("%.1f", speed), count);
        } catch (NumberFormatException exception) {
            this.lang.send(sender, "boatspeed-usage");
        }
    }

    private boolean sendBoatSpeed(Player player, double speed) {
        if (!player.getListeningPluginChannels().contains(BOATFLY_CHANNEL)) return false;
        player.sendPluginMessage(this, BOATFLY_CHANNEL, ByteBuffer.allocate(8).putDouble(speed).array());
        return true;
    }

    private boolean tpaCommand(CommandSender sender, Command command, String[] args) {
        if (!(sender instanceof Player)) { this.lang.send(sender, "player-only"); return true; }
        String name = command.getName().toLowerCase();
        if (name.equals("tpa") || name.equals("tpahere")) {
            if (!sender.hasPermission(name.equals("tpa") ? "nyaeamc.tpa" : "nyaeamc.tpahere")) { this.lang.send(sender, "no-permission"); return true; }
            if (args.length != 1) { this.lang.send(sender, name.equals("tpa") ? "tpa-usage" : "tpahere-usage"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { this.lang.send(sender, "player-notfound", args[0]); return true; }
            this.tpaManager.sendRequest((Player) sender, target, name.equals("tpa") ? TpaManager.Type.TPA : TpaManager.Type.TPAHERE);
        } else if (name.equals("tpaccept") || name.equals("tpdeny")) {
            if (!sender.hasPermission(name.equals("tpaccept") ? "nyaeamc.tpaccept" : "nyaeamc.tpdeny")) { this.lang.send(sender, "no-permission"); return true; }
            if (args.length > 1) { this.lang.send(sender, name.equals("tpaccept") ? "tpaccept-usage" : "tpdeny-usage"); return true; }
            if (name.equals("tpaccept")) this.tpaManager.accept((Player) sender, args.length == 0 ? null : args[0]);
            else this.tpaManager.deny((Player) sender, args.length == 0 ? null : args[0]);
        } else if (name.equals("tpcancel")) {
            if (!sender.hasPermission("nyaeamc.tpcancel")) { this.lang.send(sender, "no-permission"); return true; }
            if (args.length != 0) { this.lang.send(sender, "tpcancel-usage"); return true; }
            this.tpaManager.cancel((Player) sender);
        }
        return true;
    }

    // /nyaea（以及 /movel 相容別名）的 Tab 補全：依權限顯示可用子指令
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("nyaea")) return tpaTabComplete(sender, command, args);
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("nyaeamc.help")) list.add("help");
            if (sender.hasPermission("nyaeamc.toggle")) list.add("toggle");
            if (sender.hasPermission("nyaeamc.reload")) list.add("reload");
            if (sender.hasPermission("nyaeamc.note")) list.add("note");
            if (sender.hasPermission("nyaeamc.nick")) list.add("nick");
            if (sender.hasPermission("nyaeamc.nick")) list.add("realname");
            if (sender.hasPermission("nyaeamc.ping")) list.add("ping");
            if (sender.hasPermission("nyaeamc.broadcast")) list.add("broadcast");
            if (sender.hasPermission("nyaeamc.heal")) list.add("heal");
            if (sender.hasPermission("nyaeamc.feed")) list.add("feed");
            if (sender.hasPermission("nyaeamc.fly")) list.add("fly");
            if (sender.hasPermission("nyaeamc.speed")) list.add("speed");
            if (sender.hasPermission("nyaeamc.clearinventory")) list.add("clearinventory");
            if (sender.hasPermission("nyaeamc.gamemode")) list.add("gamemode");
            if (sender.hasPermission("nyaeamc.weather")) list.add("weather");
            if (sender.hasPermission("nyaeamc.time")) list.add("time");
            if (sender.hasPermission("nyaeamc.invsee")) list.add("invsee");
            if (sender.hasPermission("nyaeamc.endersee")) list.add("endersee");
            if (sender.hasPermission("nyaeamc.boatspeed")) list.add("boatspeed");
        } else if (args[0].equalsIgnoreCase("nick")) {
            if (args.length == 2) list.add("off");
            if (args.length == 2 && sender.hasPermission("nyaeamc.nick.others")) {
                for (Player player : Bukkit.getOnlinePlayers()) list.add(player.getName());
            }
        } else if (args[0].equalsIgnoreCase("ping") && args.length == 2
                && sender.hasPermission("nyaeamc.ping.others")) {
            for (Player player : Bukkit.getOnlinePlayers()) list.add(player.getName());
        } else if ((args[0].equalsIgnoreCase("heal") || args[0].equalsIgnoreCase("feed")
                || args[0].equalsIgnoreCase("fly") || args[0].equalsIgnoreCase("clearinventory")
                || args[0].equalsIgnoreCase("invsee") || args[0].equalsIgnoreCase("endersee"))
                && args.length == 2) {
            for (Player player : Bukkit.getOnlinePlayers()) list.add(player.getName());
        } else if (args[0].equalsIgnoreCase("gamemode") && args.length == 2) {
            list.add("survival");
            list.add("creative");
            list.add("adventure");
            list.add("spectator");
        } else if (args[0].equalsIgnoreCase("gamemode") && args.length == 3) {
            for (Player player : Bukkit.getOnlinePlayers()) list.add(player.getName());
        } else if (args[0].equalsIgnoreCase("weather") && args.length == 2) {
            list.add("clear");
            list.add("rain");
            list.add("thunder");
        } else if (args[0].equalsIgnoreCase("time") && args.length == 2) {
            list.add("day");
            list.add("night");
        } else if ((args[0].equalsIgnoreCase("weather") || args[0].equalsIgnoreCase("time"))
                && args.length == 3) {
            for (World world : Bukkit.getWorlds()) list.add(world.getName());
        } else if (args[0].equalsIgnoreCase("speed") && args.length == 2) {
            list.add("walk");
            list.add("fly");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("note")
                && sender.hasPermission("nyaeamc.note")) {
            list.add("list");
            list.add("remove");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("note")
                && args[1].equalsIgnoreCase("remove") && sender.hasPermission("nyaeamc.note")) {
            list.addAll(this.noteManager.getNotes().keySet());
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
        }

        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        list.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return list;
    }

    private List<String> tpaTabComplete(CommandSender sender, Command command, String[] args) {
        List<String> list = new ArrayList<>();
        String name = command.getName().toLowerCase();
        if ((name.equals("tpa") || name.equals("tpahere")) && args.length == 1) {
            for (Player player : Bukkit.getOnlinePlayers()) if (!player.equals(sender)) list.add(player.getName());
        } else if ((name.equals("tpaccept") || name.equals("tpdeny")) && args.length == 1 && sender instanceof Player) {
            list.addAll(this.tpaManager.pendingRequesterNames((Player) sender));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        list.removeIf(value -> !value.toLowerCase().startsWith(prefix));
        return list;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.isEmpty() || message.charAt(0) != '/') return;

        String[] parts = message.substring(1).split("\\s+");
        if (parts.length == 0 || !parts[0].equalsIgnoreCase("minecraft:op")) return;

        Player player = event.getPlayer();

        // 服主可在 config 關閉此功能，關閉時回歸原版行為
        if (!getConfig().getBoolean("op-command.enable", true)) return;

        if (!player.hasPermission("nyaeamc.op")) {
            this.lang.send(player, "op-no-permission");
            event.setCancelled(true);
            return;
        }

        if (parts.length < 2) {
            this.lang.send(player, "op-usage");
            event.setCancelled(true);
            return;
        }

        // 不支援備註名，僅接受真實玩家名
        String targetName = parts[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName); // 支援部分名稱
        }

        if (target == null) {
            this.lang.send(player, "op-player-notfound", parts[1]);
            event.setCancelled(true);
            return;
        }

        target.setOp(true);
        this.lang.send(player, "op-granted", target.getName());
        this.lang.send(target, "op-received", player.getName());
        event.setCancelled(true);
    }

    // 所有指令都支援備註名：把參數中符合備註名的部分換成真實玩家名（如 /kill、/tp）
    @EventHandler
    public void onPlayerCommandResolve(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        String msg = event.getMessage();
        if (msg.length() < 2 || msg.charAt(0) != '/') return;
        // 跳過插件自己的指令（備註管理），避免改動到 note 命令本身的參數
        String cmd = msg.toLowerCase();
        // 跳過插件自己的指令（備註管理）與 op 指令（已自行處理）
        if (cmd.startsWith("/nyaea ") || cmd.equals("/nyaea") || cmd.startsWith("/movel")
                || cmd.startsWith("/minecraft:op")) return;

        String[] parts = msg.substring(1).split("\\s+");
        if (parts.length < 2) return;

        boolean changed = false;
        for (int i = 1; i < parts.length; i++) {
            String arg = parts[i];
            if (arg.isEmpty()) continue;
            char c0 = arg.charAt(0);
            // 跳過選擇器(@p)、相對座標(~ ^)、負數與純數字（座標），避免誤替換
            if (c0 == '@' || c0 == '~' || c0 == '^' || c0 == '-' || arg.matches("\\d+(\\.\\d+)?")) {
                continue;
            }
            String resolved = this.noteManager.resolvePlayerName(arg);
            if (!resolved.equals(arg)) {
                parts[i] = resolved;
                changed = true;
            }
        }
        if (changed) {
            event.setMessage("/" + String.join(" ", parts));
        }
    }

    // 玩家備註管理：/movel note <玩家> <備註名> | remove | list
    private void noteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nyaeamc.note")) {
            this.lang.send(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            this.lang.send(sender, "note-usage-set");
            this.lang.send(sender, "note-usage-remove");
            this.lang.send(sender, "note-usage-list");
            return;
        }

        if (args[1].equalsIgnoreCase("list")) {
            if (this.noteManager.getNotes().isEmpty()) {
                this.lang.send(sender, "note-empty");
                return;
            }
            this.lang.send(sender, "note-list-header");
            for (var entry : this.noteManager.getNotes().entrySet()) {
                this.lang.sendRaw(sender, "note-list-item", entry.getKey(), entry.getValue());
            }
            return;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            if (args.length < 3) {
                this.lang.send(sender, "note-remove-usage");
                return;
            }
            if (this.noteManager.removeNote(args[2])) {
                this.lang.send(sender, "note-remove-success", args[2]);
            } else {
                this.lang.send(sender, "note-remove-notfound", args[2]);
            }
            return;
        }

        // /movel note <玩家> <備註名>
        if (args.length < 3) {
            this.lang.send(sender, "note-set-usage");
            return;
        }
        boolean existed = this.noteManager.setNote(args[1], args[2]);
        String action = existed ? this.lang.get("note-set-overwrite") : this.lang.get("note-set-new");
        this.lang.send(sender, "note-set-success", action, args[1], args[2]);
    }

    public void showHelp(CommandSender sender) {
        if (!sender.hasPermission("nyaeamc.help")) {
            this.lang.send(sender, "no-permission");
            return;
        }

        this.lang.sendRaw(sender, "help-title");
        this.lang.sendRaw(sender, "help-blank");
        this.lang.sendRaw(sender, "help-reload");
        this.lang.sendRaw(sender, "help-toggle");
        this.lang.sendRaw(sender, "help-note");
        this.lang.sendRaw(sender, "help-op");
        this.lang.sendRaw(sender, "help-emoji");
        this.lang.sendRaw(sender, "help-nick");
        this.lang.sendRaw(sender, "help-ping");
        this.lang.sendRaw(sender, "help-admin");
        this.lang.sendRaw(sender, "help-management");
        this.lang.sendRaw(sender, "help-boatspeed");
        this.lang.sendRaw(sender, "help-blank");
    }

    public void reload(CommandSender sender) {
        if (!sender.hasPermission("nyaeamc.reload")) {
            this.lang.send(sender, "no-permission");
            return;
        }

        reloadConfig();
        this.lang = new Lang(this);
        double savedBoatSpeed = getConfig().getDouble("boatfly.global-speed", -1.0D);
        this.globalBoatSpeed = savedBoatSpeed >= 0.1D && savedBoatSpeed <= 50.0D ? savedBoatSpeed : null;
        registerChatListener();
        if (this.lightManager != null) {
            this.lightManager.removeAllPlayerLight();
        }
        startEmojiPackServer();
        startLightTask();
        this.lang.send(sender, "reload-success");
    }

    public void toggle(CommandSender sender) {
        if (!sender.hasPermission("nyaeamc.toggle")) {
            this.lang.send(sender, "no-permission");
            return;
        }

        boolean currentState = getConfig().getBoolean("enable");
        getConfig().set("enable", !currentState);
        saveConfig();

        if (!currentState) {
            this.lang.send(sender, "toggle-on");
        } else {
            this.lang.send(sender, "toggle-off");
            if (this.lightManager != null) {
                this.lightManager.removeAllPlayerLight();
            }
        }
        startLightTask();
    }
}
