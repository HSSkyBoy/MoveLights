package top.nkbe;

import java.util.HashMap;
import java.util.Map;

/** 內建 emoji 對照表（由 tools/emoji_pack.py 產生） */
public final class EmojiData {

    public static final Map<String, String> BUILTIN = new HashMap<>();

    static {
        BUILTIN.put(":smile:", "\ud83d\ude00");
        BUILTIN.put(":smiley:", "\ud83d\ude03");
        BUILTIN.put(":joy:", "\ud83d\ude02");
        BUILTIN.put(":laughing:", "\ud83d\ude06");
        BUILTIN.put(":wink:", "\ud83d\ude09");
        BUILTIN.put(":blush:", "\ud83d\ude0a");
        BUILTIN.put(":cool:", "\ud83d\ude0e");
        BUILTIN.put(":sunglasses:", "\ud83d\ude0e");
        BUILTIN.put(":thinking:", "\ud83e\udd14");
        BUILTIN.put(":nerd:", "\ud83e\udd13");
        BUILTIN.put(":sad:", "\ud83d\ude22");
        BUILTIN.put(":cry:", "\ud83d\ude2d");
        BUILTIN.put(":angry:", "\ud83d\ude20");
        BUILTIN.put(":angryface:", "\ud83d\ude21");
        BUILTIN.put(":heart:", "\u2764\ufe0f");
        BUILTIN.put(":love:", "\ud83d\udc96");
        BUILTIN.put(":kiss:", "\ud83d\udc8b");
        BUILTIN.put(":fire:", "\ud83d\udd25");
        BUILTIN.put(":ok:", "\ud83d\udc4c");
        BUILTIN.put(":thumbsup:", "\ud83d\udc4d");
        BUILTIN.put(":thumbsdown:", "\ud83d\udc4e");
        BUILTIN.put(":clap:", "\ud83d\udc4f");
        BUILTIN.put(":wave:", "\ud83d\udc4b");
        BUILTIN.put(":pray:", "\ud83d\ude4f");
        BUILTIN.put(":cat:", "\ud83d\udc31");
        BUILTIN.put(":dog:", "\ud83d\udc36");
        BUILTIN.put(":pig:", "\ud83d\udc37");
        BUILTIN.put(":fox:", "\ud83e\udd8a");
        BUILTIN.put(":skull:", "\ud83d\udc80");
        BUILTIN.put(":ghost:", "\ud83d\udc7b");
        BUILTIN.put(":alien:", "\ud83d\udc7d");
        BUILTIN.put(":star:", "\u2b50");
        BUILTIN.put(":sun:", "\u2600\ufe0f");
        BUILTIN.put(":moon:", "\ud83c\udf19");
        BUILTIN.put(":rain:", "\ud83c\udf27\ufe0f");
        BUILTIN.put(":cloud:", "\u2601\ufe0f");
        BUILTIN.put(":snow:", "\u2744\ufe0f");
        BUILTIN.put(":zap:", "\u26a1");
        BUILTIN.put(":check:", "\u2714");
        BUILTIN.put(":x:", "\u274c");
        BUILTIN.put(":exclamation:", "\u2757");
        BUILTIN.put(":question:", "\u2753");
        BUILTIN.put(":warning:", "\u26a0\ufe0f");
        BUILTIN.put(":money:", "\ud83d\udcb0");
        BUILTIN.put(":gem:", "\ud83d\udc8e");
        BUILTIN.put(":gift:", "\ud83c\udf81");
        BUILTIN.put(":cake:", "\ud83c\udf82");
        BUILTIN.put(":beer:", "\ud83c\udf7a");
        BUILTIN.put(":pizza:", "\ud83c\udf55");
        BUILTIN.put(":game:", "\ud83c\udfae");
        BUILTIN.put(":music:", "\ud83c\udfb5");
        BUILTIN.put(":trophy:", "\ud83c\udfc6");
        BUILTIN.put(":medal:", "\ud83c\udfc5");
        BUILTIN.put(":rocket:", "\ud83d\ude80");
        BUILTIN.put(":plane:", "\u2708\ufe0f");
        BUILTIN.put(":car:", "\ud83d\ude97");
    }
}
