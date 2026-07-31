package top.nkbe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天增強：將聊天訊息中的 :emoji: 代碼轉換成 emoji 字元。
 * 內建對照表見 EmojiData（由 tools/EmojiPackTool.java 產生），
 * 也可在 config.yml 的 chat-emoji.custom 自訂。
 */
public class ChatListener implements Listener {

    private static final Pattern EMOJI_PATTERN = Pattern.compile(":[a-zA-Z0-9_]+:");

    private final Map<String, String> custom = new HashMap<>();

    public ChatListener(JavaPlugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("chat-emoji.custom");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String emoji = section.getString(key);
                if (emoji != null && !emoji.isEmpty()) {
                    // 允許直接填 :code: 或只填 code
                    custom.put(key.startsWith(":") ? key : ":" + key + ":", emoji);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        String replaced = replaceEmoji(message);
        if (!replaced.equals(message)) {
            event.setMessage(replaced);
        }
    }

    private String replaceEmoji(String message) {
        Matcher matcher = EMOJI_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String code = matcher.group();
            String emoji = custom.containsKey(code) ? custom.get(code) : EmojiData.BUILTIN.get(code);
            matcher.appendReplacement(sb, emoji != null ? Matcher.quoteReplacement(emoji) : matcher.group());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
