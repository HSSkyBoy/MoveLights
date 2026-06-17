package top.nkbe;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class MoveLights extends JavaPlugin implements Listener {

    private LightManager lightManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        startLightTask();

        // bStats
        int pluginId = 32054;
        Metrics metrics = new Metrics(this, pluginId);

        // Optional: Add custom charts
        metrics.addCustomChart(
            new Metrics.SimplePie("chart_id", () -> "My value")
        );

        Bukkit.getConsoleSender().sendMessage("§8[§aMoveLights§8] §e已啟用跨版本相容 (1.17 - 26.1.x)");
        Bukkit.getConsoleSender().sendMessage("§8[§aMoveLights§8] §a移動光源加載成功");
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
        }
        return true;
    }

    public void showHelp(CommandSender sender) {
        if (!sender.hasPermission("movelights.help")) {
            sender.sendMessage("§8[§aMoveLights§8] §c你沒有權限使用該命令");
            return;
        }

        sender.sendMessage(" §2§lMoveLights 虛擬移動光源");
        sender.sendMessage("");
        sender.sendMessage(" §7§l· §a/movel reload §6§l- §7重載插件");
        sender.sendMessage(" §7§l· §a/movel toggle §6§l- §7開關移動光源");
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
