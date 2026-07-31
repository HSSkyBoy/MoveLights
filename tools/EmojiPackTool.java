import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * 一次性開發工具：產生 MoveLights 內建的 emoji 資源包。
 * 1. 依底下 EMOJIS 清單（與聊天替換的對照表一致，是單一真源）
 *    下載 Twemoji 72x72 圖檔、縮放成 16x16、寫入
 *    resources/resourcepack/assets/movelights/textures/emoji/
 * 2. 產生 assets/minecraft/font/default.json（bitmap providers + minecraft:include）
 * 3. 產生 pack.mcmeta 與 THIRD_PARTY_NOTICES（Twemoji CC-BY 4.0）
 * 4. 產生 src/top/nkbe/EmojiData.java（ChatListener 使用的內建對照表）
 *
 * 用法（在專案根目錄）：javac -encoding UTF-8 -d tools/out tools/EmojiPackTool.java
 *                        java -cp tools/out EmojiPackTool
 */
public class EmojiPackTool {

    private static final String TWEMOJI_BASE =
            "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/";

    private static final int GLYPH_SIZE = 16;
    private static final int FONT_HEIGHT = 16;
    private static final int FONT_ASCENT = 14;

    // :code: -> emoji（單一真源，必須與聊天替換的字串一致）
    private static final String[][] EMOJIS = {
            {":smile:", "😀"}, {":smiley:", "😃"}, {":joy:", "😂"},
            {":laughing:", "😆"}, {":wink:", "😉"}, {":blush:", "😊"},
            {":cool:", "😎"}, {":sunglasses:", "😎"}, {":thinking:", "🤔"},
            {":nerd:", "🤓"}, {":sad:", "😢"}, {":cry:", "😭"},
            {":angry:", "😠"}, {":angryface:", "😡"}, {":heart:", "❤️"},
            {":love:", "💖"}, {":kiss:", "💋"}, {":fire:", "🔥"},
            {":ok:", "👌"}, {":thumbsup:", "👍"}, {":thumbsdown:", "👎"},
            {":clap:", "👏"}, {":wave:", "👋"}, {":pray:", "🙏"},
            {":cat:", "🐱"}, {":dog:", "🐶"}, {":pig:", "🐷"},
            {":fox:", "🦊"}, {":skull:", "💀"}, {":ghost:", "👻"},
            {":alien:", "👽"}, {":star:", "⭐"}, {":sun:", "☀️"},
            {":moon:", "🌙"}, {":rain:", "🌧️"}, {":cloud:", "☁️"},
            {":snow:", "❄️"}, {":zap:", "⚡"}, {":check:", "✔"},
            {":x:", "❌"}, {":exclamation:", "❗"}, {":question:", "❓"},
            {":warning:", "⚠️"}, {":money:", "💰"}, {":gem:", "💎"},
            {":gift:", "🎁"}, {":cake:", "🎂"}, {":beer:", "🍺"},
            {":pizza:", "🍕"}, {":game:", "🎮"}, {":music:", "🎵"},
            {":trophy:", "🏆"}, {":medal:", "🏅"}, {":rocket:", "🚀"},
            {":plane:", "✈️"}, {":car:", "🚗"},
    };

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        Path packRoot = Paths.get("resources/resourcepack");
        Path texDir = packRoot.resolve("assets/movelights/textures/emoji");
        Files.createDirectories(texDir);
        Files.createDirectories(packRoot.resolve("assets/minecraft/font"));

        StringBuilder font = new StringBuilder("{\n  \"providers\": [\n");
        StringBuilder data = new StringBuilder();
        data.append("package top.nkbe;\n\n")
            .append("import java.util.HashMap;\nimport java.util.Map;\n\n")
            .append("/** 內建 emoji 對照表（由 tools/EmojiPackTool.java 產生） */\n")
            .append("public final class EmojiData {\n\n")
            .append("    public static final Map<String, String> BUILTIN = new HashMap<>();\n\n")
            .append("    static {\n");

        int failed = 0;
        for (int i = 0; i < EMOJIS.length; i++) {
            String code = EMOJIS[i][0];
            String emoji = EMOJIS[i][1];
            String name = resolveFileName(emoji);

            if (name == null) {
                System.err.println("[SKIP] 找不到 Twemoji 圖檔: " + code + " (" + emoji + ")");
                failed++;
                continue;
            }

            Path png = texDir.resolve(name + ".png");
            if (!Files.exists(png)) {
                downloadResize(TWEMOJI_BASE + name + ".png", png);
            }
            System.out.println("[OK] " + code + " -> " + name + ".png");

            if (i > 0) font.append(",\n");
            font.append("    {\"type\":\"bitmap\",\"file\":\"movelights:emoji/").append(name)
                .append(".png\",\"ascent\":").append(FONT_ASCENT)
                .append(",\"height\":").append(FONT_HEIGHT)
                .append(",\"chars\":[\"").append(jsonEscape(emoji)).append("\"]}");

            data.append("        BUILTIN.put(\"").append(javaEscape(code))
                .append("\", \"").append(javaEscape(emoji)).append("\");\n");
        }

        font.append(",\n    {\"type\":\"reference\",\"file\":\"minecraft:include\"}\n")
            .append("  ]\n}\n");
        data.append("    }\n}\n");

        Files.write(packRoot.resolve("assets/minecraft/font/default.json"),
                font.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get("src/top/nkbe/EmojiData.java"),
                data.toString().getBytes(StandardCharsets.UTF_8));

        String mcmeta = "{\n  \"pack\": {\n"
                + "    \"pack_format\": 7,\n"
                + "    \"description\": \"MoveLights built-in emoji pack (Twemoji CC-BY 4.0)\"\n"
                + "  }\n}\n";
        Files.write(packRoot.resolve("pack.mcmeta"), mcmeta.getBytes(StandardCharsets.UTF_8));

        String notices = "This resource pack contains emoji artwork from Twitter Twemoji.\n"
                + "Twemoji is licensed under CC-BY 4.0 "
                + "(https://creativecommons.org/licenses/by/4.0/).\n"
                + "Source: https://github.com/twitter/twemoji\n";
        Files.write(packRoot.resolve("THIRD_PARTY_NOTICES"), notices.getBytes(StandardCharsets.UTF_8));

        System.out.println("== 完成. emoji 數: " + EMOJIS.length + ", 失敗: " + failed);
    }

    // 由 emoji 的 codepoints 推 Twemoji 檔名（小寫 hex、- 分隔）。
    // Twemoji 檔名通常省略 VS16，故優先試去掉 -fe0f 的名稱。
    private static String resolveFileName(String emoji) {
        String base = twemojiFileName(emoji);        // 例: "2600-fe0f"
        String stripped = base.replace("-fe0f", ""); // 例: "2600"
        if (exists(TWEMOJI_BASE + stripped + ".png")) return stripped;
        if (exists(TWEMOJI_BASE + base + ".png")) return base;
        return null;
    }

    private static String twemojiFileName(String emoji) {
        StringBuilder sb = new StringBuilder();
        emoji.codePoints().forEach(cp -> {
            if (sb.length() > 0) sb.append('-');
            sb.append(Integer.toHexString(cp));
        });
        return sb.toString();
    }

    private static boolean exists(String url) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("HEAD");
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);
            int code = con.getResponseCode();
            con.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static void downloadResize(String url, Path dest) throws Exception {
        byte[] bytes;
        try (InputStream in = new URL(url).openStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            bytes = out.toByteArray();
        }
        BufferedImage src = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if (src == null) throw new IOException("無法解析圖檔: " + url);
        BufferedImage scaled = new BufferedImage(GLYPH_SIZE, GLYPH_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, GLYPH_SIZE, GLYPH_SIZE, null);
        g.dispose();
        ImageIO.write(scaled, "png", dest.toFile());
    }

    // JSON 字串：非 ASCII 字元轉成反斜線 u 加四位十六進位（含 surrogate pair 逐個 code unit）
    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) sb.append(c);
            else sb.append(String.format("\\u%04x", (int) c));
        }
        return sb.toString();
    }

    // Java 字串字面值：非 ASCII 轉成反斜線 u 加四位十六進位
    private static String javaEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80 && c != '"' && c != '\\') sb.append(c);
            else if (c == '"' || c == '\\') sb.append('\\').append(c);
            else sb.append(String.format("\\u%04x", (int) c));
        }
        return sb.toString();
    }
}
