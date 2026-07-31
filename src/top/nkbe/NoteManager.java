package top.nkbe;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家備註管理：為玩家設定別名（備註名）。
 * 設定備註後，任何需要玩家名的指令都可以直接用備註名代替。
 * 資料儲存於 plugins/MoveLights/notes.yml
 */
public class NoteManager {

    private final JavaPlugin plugin;
    private final File file;
    // 備註名(小寫) -> 玩家名
    private final Map<String, String> notes = new LinkedHashMap<>();

    public NoteManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "notes.yml");
        this.load();
    }

    private void load() {
        notes.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            String value = cfg.getString(key);
            if (value != null && !value.isEmpty()) {
                notes.put(key.toLowerCase(), value);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, String> entry : notes.entrySet()) {
            cfg.set(entry.getKey(), entry.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("無法儲存 notes.yml: " + ex.getMessage());
        }
    }

    /**
     * 設定或更新玩家的備註名。
     *
     * @return 若該備註名原本已被使用則回傳 true（覆寫），否則 false
     */
    public boolean setNote(String playerName, String note) {
        String key = note.toLowerCase();
        boolean existed = notes.containsKey(key);
        notes.put(key, playerName);
        save();
        return existed;
    }

    /**
     * 移除備註。可傳入備註名或玩家名，玩家名會移除所有指向它的備註。
     *
     * @return 有移除到任何備註回傳 true
     */
    public boolean removeNote(String input) {
        String lower = input.toLowerCase();
        boolean removed = notes.remove(lower) != null;
        if (!removed) {
            removed = notes.values().removeIf(name -> name.equalsIgnoreCase(input));
        }
        if (removed) save();
        return removed;
    }

    /** 查詢某個備註名指向的玩家名，找不到回傳 null */
    public String getPlayerByNote(String note) {
        return notes.get(note.toLowerCase());
    }

    /**
     * 將指令輸入解析成真實玩家名：
     * 若輸入是備註名則回傳對應玩家名，否則原樣回傳輸入。
     */
    public String resolvePlayerName(String input) {
        String target = notes.get(input.toLowerCase());
        return (target != null) ? target : input;
    }

    /** 取得全部備註（備註名 -> 玩家名），用於列出 */
    public Map<String, String> getNotes() {
        return notes;
    }
}
