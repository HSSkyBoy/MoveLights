package top.nkbe;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * 多語言支援：依照 config 的 language 設定載入對應的訊息檔
 * (lang/zh_TW.yml、lang/zh_CN.yml、lang/en.yml)。
 * 訊息檔會先複製到插件資料夾，方便服主自行修改。
 * 支援 {0} {1} 佔位符，以及 &/§ 色碼。
 */
public class Lang {

    private final String prefix;
    private final YamlConfiguration messages;

    public Lang(JavaPlugin plugin) {
        String language = plugin.getConfig().getString("language", "zh_TW");
        String file = "lang/" + language + ".yml";
        plugin.saveResource(file, false);
        this.messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), file));
        this.prefix = get("prefix");
    }

    /** 取得訊息字串（不做 & 轉換、不替換佔位符） */
    private String raw(String key) {
        return messages.getString(key, key);
    }

    /** 取得訊息字串，支援 &/§ 色碼與 {0} {1} 佔位符 */
    public String get(String key, Object... args) {
        String msg = raw(key).replace('&', '§');
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }

    /** 送出附帶前綴的訊息 */
    public void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(prefix + get(key, args));
    }

    /** 送出不含前綴的訊息 */
    public void sendRaw(CommandSender sender, String key, Object... args) {
        sender.sendMessage(get(key, args));
    }
}
