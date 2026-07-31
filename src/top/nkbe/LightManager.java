package top.nkbe;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.Light;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class LightManager extends BukkitRunnable {

    private final JavaPlugin plugin;
    // 儲存玩家 UUID 與目前已送出的虛擬光源狀態
    private final Map<UUID, ActiveLight> activeLights = new HashMap<>();
    // 儲存設定檔中允許發光的物品資料
    private final Map<String, UsableInfo> usableItemsInfo = new HashMap<>();
    private boolean suspended = false;

    // 定義可以被光源虛擬替換的方塊類型
    private final Set<Material> replaceableBlocks = Set.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.LIGHT
    );

    // 搜尋周圍空氣的相對座標偏移量 (x, y, z)
    private final int[][] searchOffsets = {
            {0, 0, 0}, {0, 1, 0}, {0, -1, 0},
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}
    };

    private static final class ActiveLight {
        private final Location location;
        private final int level;

        private ActiveLight(Location location, int level) {
            this.location = location;
            this.level = level;
        }
    }

    public LightManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.loadConfigItems();
    }

    private void loadConfigItems() {
        this.usableItemsInfo.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("usable");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            int level = plugin.getConfig().getInt("usable." + key + ".lightLevel", 15);
            boolean apparel = plugin.getConfig().getBoolean("usable." + key + ".apparel", false);
            this.usableItemsInfo.put(key, new UsableInfo(key, level, apparel));
        }
        Bukkit.getConsoleSender().sendMessage("§8[§aNyaEeMC§8] §a已從設定檔載入 " + usableItemsInfo.size() + " 件發光物品.");
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("enable") || suspended) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("nyaemc.player.use")) {
                removePlayerLight(player);
                continue;
            }

            UsableInfo validItem = getValidLightItem(player);
            if (validItem != null) {
                updateVirtualLight(player, validItem);
            } else {
                removePlayerLight(player);
            }
        }
    }

    // 檢查玩家身上的發光體
    private UsableInfo getValidLightItem(Player player) {
        PlayerInventory inv = player.getInventory();

        // 檢查順序：主手 -> 副手 -> 頭盔 -> 胸甲 -> 護腿 -> 靴子
        Object[][] itemsToCheck = {
                {inv.getItemInMainHand(), false},
                {inv.getItemInOffHand(), false},
                {inv.getHelmet(), true},
                {inv.getChestplate(), true},
                {inv.getLeggings(), true},
                {inv.getBoots(), true}
        };

        for (Object[] checkData : itemsToCheck) {
            ItemStack item = (ItemStack) checkData[0];
            boolean isEquipSlot = (boolean) checkData[1];

            if (item == null || item.getType().isAir()) continue;

            UsableInfo info = usableItemsInfo.get(item.getType().name());
            if (info == null) continue;

            if (isEquipSlot && !info.isApparel()) continue;

            return info;
        }
        return null;
    }

    // 更新玩家的虛擬光源
    private void updateVirtualLight(Player player, UsableInfo itemInfo) {
        Location targetLoc = findAirAround(player);
        ActiveLight current = activeLights.get(player.getUniqueId());
        int level = Math.min(Math.max(itemInfo.lightLevel(), 0), 15);

        // 如果周圍找不到空氣就熄滅當前光源
        if (targetLoc == null) {
            removePlayerLight(player);
            return;
        }

        // Location.equals 也會比較 yaw/pitch；若直接使用它，玩家只是轉頭也會在每次刷新時
        // 清除並重送封包。光源只與方塊座標、世界和亮度有關。
        if (current != null && isSameBlock(targetLoc, current.location)) {
            if (current.level == level) return;
            sendVirtualLight(player, targetLoc, level);
            activeLights.put(player.getUniqueId(), new ActiveLight(targetLoc, level));
            return;
        }

        // 向玩家發送該位置在伺服器上的原方塊
        removePlayerLight(player);
        sendVirtualLight(player, targetLoc, level);
        activeLights.put(player.getUniqueId(), new ActiveLight(targetLoc, level));
    }

    private static boolean isSameBlock(Location first, Location second) {
        return first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private static void sendVirtualLight(Player player, Location location, int level) {
        Light lightData = (Light) Bukkit.createBlockData(Material.LIGHT);
        lightData.setLevel(level);
        player.sendBlockChange(location, lightData);
    }

    private Location findAirAround(Player player) {
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return null;

        int[][] offsets = searchOffsets;
        // 若玩家視角朝下，優先檢查腳下方塊
        if (loc.getDirection().getY() < -0.8) {
            offsets = searchOffsets.clone();
            int[] temp = offsets[0];
            offsets[0] = offsets[1];
            offsets[1] = temp;
        }

        for (int[] offset : offsets) {
            int x = loc.getBlockX() + offset[0];
            int y = loc.getBlockY() + offset[1];
            int z = loc.getBlockZ() + offset[2];
            if (replaceableBlocks.contains(loc.getWorld().getBlockAt(x, y, z).getType())) {
                // 使用方塊座標，避免把玩家旋轉角度帶入光源狀態。
                return new Location(loc.getWorld(), x, y, z);
            }
        }
        return null;
    }

    // 清除指定玩家的虛擬光源
    public void removePlayerLight(Player player) {
        ActiveLight current = activeLights.remove(player.getUniqueId());
        if (current != null) {
            // 同步伺服器真實方塊
            player.sendBlockChange(current.location, current.location.getBlock().getBlockData());
        }
    }

    // 清除所有玩家的虛擬光源
    public void removeAllPlayerLight() {
        this.suspended = true;
        List<UUID> uuids = new ArrayList<>(activeLights.keySet());
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removePlayerLight(player);
            }
        }
        activeLights.clear();
        this.suspended = false;
    }
}
