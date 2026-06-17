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
    // 儲存玩家 UUID 與對應虛擬光源的座標
    private final Map<UUID, Location> activeLights = new HashMap<>();
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
        Bukkit.getConsoleSender().sendMessage("§8[§aMoveLights§8] §a已從設定檔載入 " + usableItemsInfo.size() + " 件發光物品.");
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("enable") || suspended) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("movelights.player.use")) {
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
        Location currentLoc = activeLights.get(player.getUniqueId());

        // 如果周圍找不到空氣就熄滅當前光源
        if (targetLoc == null) {
            removePlayerLight(player);
            return;
        }

        if (targetLoc.equals(currentLoc)) return;

        // 向玩家發送該位置在伺服器上的原方塊
        removePlayerLight(player);

        // 創建虛擬的 Light 方塊
        Light lightData = (Light) Bukkit.createBlockData(Material.LIGHT);
        lightData.setLevel(Math.min(Math.max(itemInfo.lightLevel(), 0), 15)); // 確保亮度在 0-15 之間

        // 僅發送封包給客戶端
        player.sendBlockChange(targetLoc, lightData);
        activeLights.put(player.getUniqueId(), targetLoc);
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
            Location target = loc.clone().add(offset[0], offset[1], offset[2]);
            if (replaceableBlocks.contains(target.getBlock().getType())) {
                return target;
            }
        }
        return null;
    }

    // 清除指定玩家的虛擬光源
    public void removePlayerLight(Player player) {
        Location currentLoc = activeLights.remove(player.getUniqueId());
        if (currentLoc != null) {
            // 同步伺服器真實方塊
            player.sendBlockChange(currentLoc, currentLoc.getBlock().getBlockData());
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
