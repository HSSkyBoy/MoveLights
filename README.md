# NyaEeMC - 虚拟移动光源 + 聊天增强
NyaEeMC 是一款专为 Minecraft 伺服器设计的轻量化工具插件（前身 MoveLights）。它利用「虚拟封包模式」，让玩家在手持或穿戴发光物品时，能够照亮周围环境，且不会对地图方块造成任何实质变更。同时内建**聊天增强**功能（emoji 转换、玩家备注、`/minecraft:op` 授权）。


## 🌟 核心特性
* **虚拟封包技术**：透过 `sendBlockChange` 向客户端发送虚拟的 `LIGHT` 方块资讯。这意味着光源只存在于玩家的视图中，不会在伺服器端产生真正的方块更替，完美保护地图完整性并降低伺服器负担。
* **多样化发光检测**：支援主手、副手以及全身装备栏（头盔、胸甲、护腿、靴子）的物品检测。
* **高度自定义**：可自由设定哪些物品具备发光功能、发光的亮度等级（0-15），以及物品是否必须「穿戴」才生效。
* **智能搜寻演算**：自动搜寻玩家周遭的空气、洞穴空气或虚空空气来放置虚拟光源，并会根据玩家视角（如俯视时）优化光源位置。
* **聊天 emoji**：聊天时输入 `:smile:`、`:heart:` 等代码自动转换成 emoji，对照表可自定义。
* **玩家备注**：为玩家设定备注名，之后所有需要玩家名的指令（如 `/kill`、`/tp`）都能直接用备注名代替。

## 🛠️ 安装与环境
* **API 版本**：适用于 Spigot/Paper 1.17 - 26.2.x。
* **依赖要求**：无需额外插件。

## 🎮 指令与权限
### 指令表
| 指令 | 说明 | 权限要求 |
| :--- | :--- | :--- |
| `/nyaee help` | 显示插件帮助选单 | `nyaemc.help` |
| `/nyaee toggle` | 全域开启或关闭移动光源功能 | `nyaemc.toggle` |
| `/nyaee reload` | 重载设定档并重新启动光源任务 | `nyaemc.reload` |
| `/nyaee note <玩家> <备注名>` | 为玩家设定备注名 | `nyaemc.note` |
| `/nyaee note remove <玩家\|备注名>` | 移除备注 | `nyaemc.note` |
| `/nyaee note list` | 列出所有备注 | `nyaemc.note` |
| `/nick <暱稱\|off>` 或 `/nyaee nick <暱稱\|off>` | 設定或清除自己的暱稱 | `nyaemc.nick` |
| `/nick <玩家> <暱稱\|off>` 或 `/nyaee nick <玩家> <暱稱\|off>` | 設定或清除其他玩家的暱稱 | `nyaemc.nick.others` |
| `/realname <暱稱>` 或 `/nyaee realname <暱稱>` | 由暱稱查詢真實玩家名 | `nyaemc.nick` |
| `/ping [玩家]` 或 `/nyaee ping [玩家]` | 查看延遲 | `nyaemc.ping`（查他人需 `nyaemc.ping.others`） |
| `/broadcast <訊息>` 或 `/nyaee broadcast <訊息>` | 發送全服公告 | `nyaemc.broadcast` |
| `/heal [玩家]` 或 `/nyaee heal [玩家]` | 治療並滅火 | `nyaemc.heal` |
| `/feed [玩家]` 或 `/nyaee feed [玩家]` | 補滿飢餓與飽和度 | `nyaemc.feed` |
| `/fly [玩家]` 或 `/nyaee fly [玩家]` | 切換飛行 | `nyaemc.fly` |
| `/speed [walk\|fly] <1-10>` 或 `/nyaee speed ...` | 設定自己的移動速度 | `nyaemc.speed` |
| `/clearinventory [玩家]` 或 `/nyaee clearinventory [玩家]` | 清空背包 | `nyaemc.clearinventory` |
| `/boatspeed <0.1-50>` 或 `/nyaee boatspeed ...` | 強制 BoatFly 全局速度 | `nyaemc.boatspeed` |

> 主指令为 `/nyaee`；`/nyae`、`/nyaemc`、`/movel` 保留为兼容别名。

### 其他权限
* `nyaemc.player.use`：玩家是否能使用移动光源功能的基础权限（预设所有人拥有）。
* `nyaemc.note`：是否可设定/管理玩家备注（预设只有 op）。
* `nyaemc.nick.color`：是否可在暱稱使用 `&` 色碼（預設只有 op）。
* `nyaemc.chat.color`：是否可在聊天與公告使用 `&` 色碼（預設只有 op）。
* `nyaemc.boatspeed`：是否可強制控制安裝新版 BoatFly 的玩家速度（預設只有 op）。

### 😀 聊天 emoji 怎麼用
在聊天室輸入 `:代碼:`，外層的冒號不可省略，插件便會自動轉成 emoji。例如：

```
肚子好餓 :pizza:
今天運氣不錯 :star: 加油 :thumbsup:
```

會顯示為「肚子好餓 🍕」與「今天運氣不錯 ⭐ 加油 👍」。

| 類別 | 可用代碼 |
| :--- | :--- |
| 表情 | `:smile:` `:smiley:` `:joy:` `:laughing:` `:wink:` `:blush:` `:cool:` `:sunglasses:` `:thinking:` `:nerd:` `:sad:` `:cry:` `:angry:` `:angryface:` |
| 愛心 | `:heart:` `:love:` `:kiss:` |
| 動作 | `:fire:` `:ok:` `:thumbsup:` `:thumbsdown:` `:clap:` `:wave:` `:pray:` |
| 動物 | `:cat:` `:dog:` `:pig:` `:fox:` |
| 鬼怪 | `:skull:` `:ghost:` `:alien:` |
| 天氣 | `:star:` `:sun:` `:moon:` `:rain:` `:cloud:` `:snow:` `:zap:` |
| 標誌 | `:check:` `:x:` `:exclamation:` `:question:` `:warning:` |
| 物品 | `:money:` `:gem:` `:gift:` `:cake:` `:beer:` `:pizza:` `:game:` `:music:` `:trophy:` `:medal:` `:rocket:` `:plane:` `:car:` |

Java 玩家需要接受插件推送的內建 Fluent Emoji 3D 資源包，才能看到彩色 emoji；請確認伺服器防火牆已開放 `emoji-pack.port`（預設 `8399`）。Bedrock／Geyser 跨端玩家原生支援 emoji，不需要資源包。

可透過 config 的 `chat-emoji.custom` 新增或覆蓋對照，例如：

```yaml
chat-emoji:
  enable: true
  custom:
    ":tick:": "✔"
    ":doge:": "🐶"
```

插件内建 **emoji 资源包**（Microsoft Fluent Emoji 3D，MIT），启动后会自动把资源包推送给 Java 端玩家，让彩色 emoji 正常显示；**Bedrock/Geyser 跨端原生支持 emoji**，不受影响。相关设置：

```yaml
emoji-pack:
  enable: true     # 是否启动内建资源包伺服器并推送
  port: 8399       # 资源包伺服器端口
  host: ""         # 伺服器对外位址，留空自动侦测；公网/NAT 建议填网域或公网 IP
```

> 注意：资源包伺服器端口需在防火墙/机房开放，公网服务器建议在 `host` 填对外网域或 IP。

若 emoji 沒有顯示：確認使用最新版 jar、`emoji-pack.enable: true`、玩家已接受資源包，並檢查 `emoji-pack.host` 是否為玩家可連線的公開位址。代碼拼錯或漏掉冒號時，文字會維持原樣。

### 💬 聊天與暱稱
`/nick <暱稱>` 或 `/nyaee nick <暱稱>` 會同步更新聊天、Tab 列表與玩家頭上的顯示名稱，資料保存在 `nicknames.yml`，重進伺服器後仍會套用。以 `/nick off` 或 `/nyaee nick off` 還原真實名稱。

```yaml
chat:
  color:
    enable: true
  join-message: "&e{0} 加入了伺服器" # 使用 {0} 顯示暱稱
  quit-message: "&e{0} 離開了伺服器"
```

### 🚤 BoatFly 全局速度

伺服器安裝 NyaEeMC、玩家安裝含聯動功能的新版 BoatFly 後，管理員可用 `/boatspeed 18.5` 強制所有已安裝該模組的在線玩家速度為 18.5；可用範圍為 `0.1–50`。設定會保存於 `boatfly.global-speed`，玩家重新加入時也會重新套用。未安裝新版 BoatFly 的玩家不受影響。

## ⚙️ 设定档说明 (`config.yml`)
您可以透过设定档精细控制插件行为：

```yaml
# 语言 (zh_TW / zh_CN / en)，讯息档在插件目录 lang/ 下可自行修改
language: zh_TW

# 是否启用移动光源功能
enable: true

# 刷新速度 (单位：Tick)
# 20 Ticks = 1 秒。数值越低效果越流畅，但 CPU 消耗会随之增加。
refresh: 5

# 发光物品清单
usable:
  TORCH: # 物品 ID
    lightLevel: 14  # 亮度等级 (0 ~ 15)
    apparel: false  # 是否必须穿戴在装备栏才有效 (false 代表手持也有效)
  GOLDEN_HELMET:
    lightLevel: 9
    apparel: true   # 设定为 true，则放在手持栏位不会发光
```


## 💡 运作逻辑
1.  **检测**：插件以设定的 `refresh` 频率扫描玩家手中的物品与装备。
2.  **判定**：优先顺序为 **主手 > 副手 > 头盔 > 胸甲 > 护腿 > 靴子**。
3.  **渲染**：找到合法物品后，插件会在玩家周围寻找可替换的空间（如空气），并发送一个虚拟的 `LIGHT` 方块封包给该玩家。
4.  **清除**：当玩家移动或切换物品时，旧的虚拟光源会自动被伺服器真实的方块资讯覆盖，确保不会残留视觉错误。

## 原作者
[FlyingQwQ](https://github.com/FlyingQwQ/MoveLight)


## 使用统计 (几乎没有)
![bstats](https://bstats.org/signatures/bukkit/MoveLights.svg)
