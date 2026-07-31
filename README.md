# MoveLights - 虚拟移动光源插件
MoveLights 是一款专为 Minecraft 伺服器设计的轻量化移动光源插件。它利用「虚拟封包模式」，让玩家在手持或穿戴发光物品时，能够照亮周围环境，且不会对地图方块造成任何实质变更。同时内建**聊天增强**功能（emoji 转换、玩家备注、`/minecraft:op` 授权）。


## 🌟 核心特性
* **虚拟封包技术**：透过 `sendBlockChange` 向客户端发送虚拟的 `LIGHT` 方块资讯。这意味着光源只存在于玩家的视图中，不会在伺服器端产生真正的方块更替，完美保护地图完整性并降低伺服器负担。
* **多样化发光检测**：支援主手、副手以及全身装备栏（头盔、胸甲、护腿、靴子）的物品检测。
* **高度自定义**：可自由设定哪些物品具备发光功能、发光的亮度等级（0-15），以及物品是否必须「穿戴」才生效。
* **智能搜寻演算**：自动搜寻玩家周遭的空气、洞穴空气或虚空空气来放置虚拟光源，并会根据玩家视角（如俯视时）优化光源位置。
* **聊天 emoji**：聊天时输入 `:smile:`、`:heart:` 等代码自动转换成 emoji，对照表可自定义。
* **玩家备注**：为玩家设定备注名，之后所有需要玩家名的指令（如 `/minecraft:op`）都能直接用备注名代替。
* **`/minecraft:op` 授权**：可让指定玩家（预设所有人）直接授予其他玩家 op 权限，服主可透过权限节点或 config 控制。

## 🛠️ 安装与环境
* **API 版本**：适用于 Spigot/Paper 1.17 - 26.2.x。
* **依赖要求**：无需额外插件。

## 🎮 指令与权限
### 指令表
| 指令 | 说明 | 权限要求 |
| :--- | :--- | :--- |
| `/movel help` | 显示插件帮助选单 | `movelights.help` |
| `/movel toggle` | 全域开启或关闭移动光源功能 | `movelights.toggle` |
| `/movel reload` | 重载设定档并重新启动光源任务 | `movelights.reload` |
| `/movel note <玩家> <备注名>` | 为玩家设定备注名 | `movelights.note` |
| `/movel note remove <玩家\|备注名>` | 移除备注 | `movelights.note` |
| `/movel note list` | 列出所有备注 | `movelights.note` |
| `/minecraft:op <玩家\|备注名>` | 授予玩家 op 权限（默认所有人可用） | `movelights.op` |

### 其他权限
* `movelights.player.use`：玩家是否能使用移动光源功能的基础权限（预设所有人拥有）。
* `movelights.op`：是否可使用 `/minecraft:op` 授予 op（预设所有人，服主可收回）。
* `movelights.note`：是否可设定/管理玩家备注（预设只有 op）。

### 😀 聊天 emoji
聊天时输入 `:smile:`、`:heart:`、`:fire:` 等代码会自动转换成 emoji。可透过 config 的 `chat-emoji.custom` 新增或覆盖对应对照，例如：

```yaml
chat-emoji:
  enable: true
  custom:
    ":tick:": "✔"
```

插件内建 **emoji 资源包**（Twemoji，CC-BY 4.0），启动后会自动把资源包推送给 Java 端玩家，让彩色 emoji 正常显示；**Bedrock/Geyser 跨端原生支持 emoji**，不受影响。相关设置：

```yaml
emoji-pack:
  enable: true     # 是否启动内建资源包伺服器并推送
  port: 8399       # 资源包伺服器端口
  host: ""         # 伺服器对外位址，留空自动侦测；公网/NAT 建议填网域或公网 IP
```

> 注意：资源包伺服器端口需在防火墙/机房开放，公网服务器建议在 `host` 填对外网域或 IP。

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
