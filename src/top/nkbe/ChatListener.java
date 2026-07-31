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
 * 內建常用對照表，也可在 config.yml 的 chat-emoji.custom 自訂。
 */
public class ChatListener implements Listener {

    private static final Pattern EMOJI_PATTERN = Pattern.compile(":[a-zA-Z0-9_]+:");

    // 內建對照表
    private static final Map<String, String> BUILTIN = new HashMap<>();

    static {
        BUILTIN.put(":smile:", "😀");
        BUILTIN.put(":smiley:", "😃");
        BUILTIN.put(":joy:", "😂");
        BUILTIN.put(":laughing:", "😆");
        BUILTIN.put(":wink:", "😉");
        BUILTIN.put(":blush:", "😊");
        BUILTIN.put(":cool:", "😎");
        BUILTIN.put(":sunglasses:", "😎");
        BUILTIN.put(":thinking:", "🤔");
        BUILTIN.put(":nerd:", "🤓");
        BUILTIN.put(":sad:", "😢");
        BUILTIN.put(":cry:", "😭");
        BUILTIN.put(":angry:", "😠");
        BUILTIN.put(":angryface:", "😡");
        BUILTIN.put(":heart:", "❤️");
        BUILTIN.put(":love:", "💖");
        BUILTIN.put(":kiss:", "💋");
        BUILTIN.put(":fire:", "🔥");
        BUILTIN.put(":ok:", "👌");
        BUILTIN.put(":thumbsup:", "👍");
        BUILTIN.put(":thumbsdown:", "👎");
        BUILTIN.put(":clap:", "👏");
        BUILTIN.put(":wave:", "👋");
        BUILTIN.put(":pray:", "🙏");
        BUILTIN.put(":cat:", "🐱");
        BUILTIN.put(":dog:", "🐶");
        BUILTIN.put(":pig:", "🐷");
        BUILTIN.put(":fox:", "🦊");
        BUILTIN.put(":skull:", "💀");
        BUILTIN.put(":ghost:", "👻");
        BUILTIN.put(":alien:", "👽");
        BUILTIN.put(":star:", "⭐");
        BUILTIN.put(":sun:", "☀️");
        BUILTIN.put(":moon:", "🌙");
        BUILTIN.put(":rain:", "🌧️");
        BUILTIN.put(":cloud:", "☁️");
        BUILTIN.put(":snow:", "❄️");
        BUILTIN.put(":zap:", "⚡");
        BUILTIN.put(":check:", "✔");
        BUILTIN.put(":x:", "❌");
        BUILTIN.put(":exclamation:", "❗");
        BUILTIN.put(":question:", "❓");
        BUILTIN.put(":warning:", "⚠️");
        BUILTIN.put(":money:", "💰");
        BUILTIN.put(":gem:", "💎");
        BUILTIN.put(":gift:", "🎁");
        BUILTIN.put(":cake:", "🎂");
        BUILTIN.put(":beer:", "🍺");
        BUILTIN.put(":pizza:", "🍕");
        BUILTIN.put(":game:", "🎮");
        BUILTIN.put(":music:", "🎵");
        BUILTIN.put(":trophy:", "🏆");
        BUILTIN.put(":medal:", "🏅");
        BUILTIN.put(":rocket:", "🚀");
        BUILTIN.put(":plane:", "✈️");
        BUILTIN.put(":car:", "🚗");
    }

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
            String emoji = custom.containsKey(code) ? custom.get(code) : BUILTIN.get(code);
            matcher.appendReplacement(sb, emoji != null ? Matcher.quoteReplacement(emoji) : matcher.group());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
