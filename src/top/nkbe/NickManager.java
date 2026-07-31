package top.nkbe;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 儲存玩家暱稱，並同步套用至聊天顯示與 Tab 玩家列表。 */
public class NickManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, String> nicknames = new LinkedHashMap<>();

    public NickManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nicknames.yml");
        load();
    }

    private void load() {
        nicknames.clear();
        if (!file.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                String nickname = config.getString(key);
                if (nickname != null && !nickname.isEmpty()) nicknames.put(UUID.fromString(key), nickname);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("略過 nicknames.yml 中無效的 UUID: " + key);
            }
        }
    }

    private void save() {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("無法儲存 nicknames.yml: " + exception.getMessage());
        }
    }

    public void setNickname(Player player, String nickname) {
        nicknames.put(player.getUniqueId(), nickname);
        save();
        apply(player);
    }

    public boolean clearNickname(Player player) {
        if (nicknames.remove(player.getUniqueId()) == null) return false;
        save();
        apply(player);
        return true;
    }

    public String getNickname(Player player) {
        return nicknames.get(player.getUniqueId());
    }

    public String getRealName(String nickname) {
        String wanted = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', nickname));
        for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
            String saved = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', entry.getValue()));
            if (saved.equalsIgnoreCase(wanted)) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                return player.getName();
            }
        }
        return null;
    }

    public void apply(Player player) {
        String nickname = getNickname(player);
        if (nickname == null) {
            player.setDisplayName(player.getName());
            player.setPlayerListName(null);
            player.setCustomName(null);
            player.setCustomNameVisible(false);
            return;
        }
        String colored = ChatColor.translateAlternateColorCodes('&', nickname);
        player.setDisplayName(colored);
        player.setPlayerListName(colored);
        player.setCustomName(colored);
        player.setCustomNameVisible(true);
    }
}
