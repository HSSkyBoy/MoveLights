#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
產生 MoveLights 內建的 emoji 資源包（Microsoft Fluent Emoji 3D，MIT 授權）。

EMOJI 清單是聊天替換的「單一真源」：會同時產生
  - assets/movelights/textures/emoji/<codepoint>.png   (24x24 圖檔)
  - assets/minecraft/font/default.json                 (字型 providers)
  - src/top/nkbe/EmojiData.java                        (ChatListener 用對照表)

用法（在專案根目錄）：python tools/emoji_pack.py
之後再用 PowerShell 重新打包 resourcepack.zip：
  Compress-Archive -Path "resources/resourcepack/*" -DestinationPath "resources/resourcepack.zip" -Force
"""
import json
import os
import re
import urllib.parse
import urllib.request
from io import BytesIO

from PIL import Image

FLUENT_BASE = "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/"
TREE_URL = "https://api.github.com/repos/microsoft/fluentui-emoji/git/trees/main?recursive=1"

GLYPH_SIZE = 24
FONT_HEIGHT = 24
FONT_ASCENT = 21

# (:code:, emoji, Fluent 資料夾名稱)
EMOJI = [
    (":smile:", "😀", "Grinning face"),
    (":smiley:", "😃", "Grinning face with big eyes"),
    (":joy:", "😂", "Face with tears of joy"),
    (":laughing:", "😆", "Grinning squinting face"),
    (":wink:", "😉", "Winking face"),
    (":blush:", "😊", "Smiling face with smiling eyes"),
    (":cool:", "😎", "Smiling face with sunglasses"),
    (":sunglasses:", "😎", "Smiling face with sunglasses"),
    (":thinking:", "🤔", "Thinking face"),
    (":nerd:", "🤓", "Nerd face"),
    (":sad:", "😢", "Crying face"),
    (":cry:", "😭", "Loudly crying face"),
    (":angry:", "😠", "Angry face"),
    (":angryface:", "😡", "Pouting face"),
    (":heart:", "❤️", "Red heart"),
    (":love:", "💖", "Sparkling heart"),
    (":kiss:", "💋", "Kiss mark"),
    (":fire:", "🔥", "Fire"),
    (":ok:", "👌", "Ok hand"),
    (":thumbsup:", "👍", "Thumbs up"),
    (":thumbsdown:", "👎", "Thumbs down"),
    (":clap:", "👏", "Clapping hands"),
    (":wave:", "👋", "Waving hand"),
    (":pray:", "🙏", "Folded hands"),
    (":cat:", "🐱", "Cat face"),
    (":dog:", "🐶", "Dog face"),
    (":pig:", "🐷", "Pig face"),
    (":fox:", "🦊", "Fox"),
    (":skull:", "💀", "Skull"),
    (":ghost:", "👻", "Ghost"),
    (":alien:", "👽", "Alien"),
    (":star:", "⭐", "Star"),
    (":sun:", "☀️", "Sun"),
    (":moon:", "🌙", "Crescent moon"),
    (":rain:", "🌧️", "Cloud with rain"),
    (":cloud:", "☁️", "Cloud"),
    (":snow:", "❄️", "Snowflake"),
    (":zap:", "⚡", "High voltage"),
    (":check:", "✔", "Check mark"),
    (":x:", "❌", "Cross mark"),
    (":exclamation:", "❗", "Red exclamation mark"),
    (":question:", "❓", "Red question mark"),
    (":warning:", "⚠️", "Warning"),
    (":money:", "💰", "Money bag"),
    (":gem:", "💎", "Gem stone"),
    (":gift:", "🎁", "Wrapped gift"),
    (":cake:", "🎂", "Birthday cake"),
    (":beer:", "🍺", "Beer mug"),
    (":pizza:", "🍕", "Pizza"),
    (":game:", "🎮", "Video game"),
    (":music:", "🎵", "Musical note"),
    (":trophy:", "🏆", "Trophy"),
    (":medal:", "🏅", "Sports medal"),
    (":rocket:", "🚀", "Rocket"),
    (":plane:", "✈️", "Airplane"),
    (":car:", "🚗", "Automobile"),
]


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "MoveLights-tool"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def codepoint_name(emoji):
    """emoji -> 小寫 hex codepoints，用 - 連接（檔名用）"""
    return "-".join("%x" % ord(ch) for ch in emoji)


def json_escape_emoji(emoji):
    """emoji -> JSON 字串，每個 UTF-16 code unit 轉成 \\uXXXX"""
    data = emoji.encode("utf-16-be")
    out = []
    for i in range(0, len(data), 2):
        unit = (data[i] << 8) | data[i + 1]
        out.append("\\u%04x" % unit)
    return "".join(out)


def java_escape(s):
    """一般字串 -> Java 字串字面值，非 ASCII 轉 \\uXXXX"""
    out = []
    for ch in s:
        code = ord(ch)
        if ch in ('"', "\\"):
            out.append("\\" + ch)
        elif 32 <= code < 128:
            out.append(ch)
        else:
            data = ch.encode("utf-16-be")
            for i in range(0, len(data), 2):
                unit = (data[i] << 8) | data[i + 1]
                out.append("\\u%04x" % unit)
    return "".join(out)


def main():
    pack_root = os.path.join("resources", "resourcepack")
    tex_dir = os.path.join(pack_root, "assets", "movelights", "textures", "emoji")
    font_dir = os.path.join(pack_root, "assets", "minecraft", "font")
    os.makedirs(tex_dir, exist_ok=True)
    os.makedirs(font_dir, exist_ok=True)

    # 清空舊圖檔
    for f in os.listdir(tex_dir):
        os.remove(os.path.join(tex_dir, f))

    print("下載 git tree ...")
    tree = json.loads(fetch(TREE_URL).decode("utf-8"))
    png3d = {}  # folder -> 3D png 相對路徑
    for entry in tree["tree"]:
        p = entry["path"]
        m = re.match(r"^assets/([^/]+)/3D/([^/]+\.png)$", p)
        if m:
            png3d.setdefault(m.group(1), p)
            continue
        m = re.match(r"^assets/([^/]+)/Default/3D/([^/]+\.png)$", p)
        if m:
            png3d.setdefault(m.group(1), p)

    providers = []
    data_lines = []
    failed = 0

    for code, emoji, folder in EMOJI:
        png_rel = png3d.get(folder)
        if png_rel is None:
            print("[SKIP] 找不到 3D PNG: %s (%s)" % (folder, emoji))
            failed += 1
            continue

        out_file = os.path.join(tex_dir, codepoint_name(emoji) + ".png")
        try:
            raw = fetch(FLUENT_BASE + urllib.parse.quote(png_rel))
            img = Image.open(BytesIO(raw)).convert("RGBA")
            img = img.resize((GLYPH_SIZE, GLYPH_SIZE), Image.LANCZOS)
            img.save(out_file)
        except Exception as exc:
            print("[FAIL] %s (%s): %s" % (folder, emoji, exc))
            failed += 1
            continue

        providers.append(
            '{"type":"bitmap","file":"movelights:emoji/%s.png","ascent":%d,"height":%d,'
            '"chars":["%s"]}'
            % (codepoint_name(emoji), FONT_ASCENT, FONT_HEIGHT, json_escape_emoji(emoji))
        )
        data_lines.append(
            '        BUILTIN.put("%s", "%s");'
            % (java_escape(code), java_escape(emoji))
        )
        print("[OK] %s -> %s.png" % (code, codepoint_name(emoji)))

    font_json = "{\n  \"providers\": [\n    " + ",\n    ".join(providers)
    font_json += ',\n    {"type":"reference","file":"minecraft:include"}\n  ]\n}\n'
    with open(os.path.join(font_dir, "default.json"), "w", encoding="utf-8") as f:
        f.write(font_json)

    mcmeta = '{\n  "pack": {\n    "pack_format": 7,\n'
    mcmeta += '    "description": "MoveLights built-in emoji pack (Fluent Emoji, MIT)"\n'
    mcmeta += "  }\n}\n"
    with open(os.path.join(pack_root, "pack.mcmeta"), "w", encoding="utf-8") as f:
        f.write(mcmeta)

    notices = (
        "This resource pack contains emoji artwork from Microsoft Fluent Emoji.\n"
        "Fluent Emoji is licensed under the MIT License.\n"
        "Source: https://github.com/microsoft/fluentui-emoji\n"
        "License text: https://github.com/microsoft/fluentui-emoji/blob/main/LICENSE\n\n"
        "MIT License\n\n"
        "Copyright (c) Microsoft Corporation\n\n"
        "Permission is hereby granted, free of charge, to any person obtaining a copy\n"
        "of this software and associated documentation files (the \"Software\"), to deal\n"
        "in the Software without restriction, including without limitation the rights\n"
        "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n"
        "copies of the Software, and to permit persons to whom the Software is\n"
        "furnished to do so, subject to the following conditions:\n\n"
        "The above copyright notice and this permission notice shall be included in all\n"
        "copies or substantial portions of the Software.\n\n"
        "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n"
        "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n"
        "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n"
        "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n"
        "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n"
        "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n"
        "SOFTWARE.\n"
    )
    with open(os.path.join(pack_root, "THIRD_PARTY_NOTICES"), "w", encoding="utf-8") as f:
        f.write(notices)

    emoji_data = (
        "package top.nkbe;\n\n"
        "import java.util.HashMap;\nimport java.util.Map;\n\n"
        "/** 內建 emoji 對照表（由 tools/emoji_pack.py 產生） */\n"
        "public final class EmojiData {\n\n"
        "    public static final Map<String, String> BUILTIN = new HashMap<>();\n\n"
        "    static {\n"
        + "\n".join(data_lines)
        + "\n    }\n}\n"
    )
    with open(os.path.join("src", "top", "nkbe", "EmojiData.java"), "w", encoding="utf-8") as f:
        f.write(emoji_data)

    print("== 完成. emoji 數: %d, 失敗: %d ==" % (len(EMOJI), failed))
    print("請重新打包: Compress-Archive -Path 'resources/resourcepack/*' "
          "-DestinationPath 'resources/resourcepack.zip' -Force")


if __name__ == "__main__":
    main()
