# Minecraft 26.1.2 + Meteor Client 26.1.2-SNAPSHOT 中文开发参考资料库

> 本资料库是 yiyiaddon 项目以后所有客户端 addon 开发/修 Bug/查 API 的**第一优先事实来源**。
> 用途：查 Minecraft API、查 Meteor API、修 Bug、开发新 Module、开发新 Addon、查 Packet/Event/Mixin/Player/Entity/Block/Item/Inventory/Screen/DataComponent/Enchantment/Registry/Movement/Network 及原生机制。

---

## 一、版本基准（唯一，禁止混入其他版本）

| 项目 | 版本 |
|------|------|
| Minecraft | 26.1.2（内部兼容标识 1.21.11，非混淆官方发布） |
| 映射 | Mojang 官方映射（**不是 Yarn**，26.1 起 Yarn 退役） |
| Fabric Loader | 0.19.3 |
| Meteor Client | 26.1.2-SNAPSHOT |
| 事件库 | orbit 0.2.4（meteordevelopment.orbit） |
| Java | 25 |

禁止混入：Minecraft 旧版本、Yarn mappings、旧版 Meteor、第三方旧 API、网上过时教程、无法确认版本的代码。

---

## 二、目录结构

```
26.1.2-开发参考库/
├── README.md                   本文件：总入口 + 查询顺序铁律
├── Minecraft原始源码/          Minecraft 26.1.2 官方源码完整保留（6882 个 .java，Mojang 官方映射）
│   ├── net/minecraft/...       主体源码
│   └── com/mojang/...          数据修复器（DataFixers）等
├── Meteor原始源码/             Meteor Client 26.1.2 完整源码（950 个 .java）
│   ├── meteordevelopment/meteorclient/   Meteor 本体
│   └── meteordevelopment/orbit/          orbit 事件系统源码
├── API参考/
│   ├── Minecraft/               15 篇：玩家/实体/世界与方块/物品与物品栏/数据组件/附魔/网络/数据包/移动/碰撞/注册表/命令/GUI与界面/Mixin/其他
│   └── Meteor/                  14 篇：Module模块/Setting设置/Event事件/Packet数据包/Network网络/玩家工具/物品栏工具/方块工具/实体工具/渲染/命令/Addon/Mixin/其他
├── 快速索引/                    类/方法/字段/数据包/事件/Mixin/注册表/数据组件/Setting/Module 索引 + 问题检索地图
├── 开发机制/                    20+ 篇机制文档（玩家移动/网络/数据包生命周期/位置同步/物品栏/容器同步/数据组件/附魔/Module生命周期/Event生命周期/Setting系统/Command系统/Tick/世界切换/常见错误/版本规则）
├── 真实代码示例/                10 篇从真实源码提取的开发模式示例
├── 工具/                        整合的 API 查询工具（查JARAPI.js 等）与分类速查
├── 生成的索引文件/              类名索引 9848 类 / 简名对照 / 易错对照表（查JARAPI.js 依赖）
└── 报告/                        2026-09-03-资料库建立报告.md
```

---

## 三、「防止 AI 乱猜 API」查询顺序铁律

以后 AI 开发时**必须按此顺序**，任何一步能找到答案就停下，禁止跳到猜：

1. **第一步：查快速索引**（类索引 → 定位类与源码路径）
2. **第二步：查 API参考**（对应领域文档，看真实签名与用途）
3. **第三步：查开发机制**（理解该机制怎么运转）
4. **第四步：直接打开原始源码确认**（最后事实依据）

冲突裁决规则（优先级从高到低）：

| 冲突 | 裁决 |
|------|------|
| 模型记忆 vs 资料库 | **资料库为准** |
| 快速索引 vs 原始源码 | **原始源码为准** |
| API 文档 vs 原始源码 | **原始源码为准** |
| 旧版本教程 vs 26.1.2 源码 | **26.1.2 源码为准** |

其他铁律：

- 资料库中**找不到某个 API，不能直接认为不存在**——必须继续搜原始源码（Grep 简名/全名/方法名）。
- 资料库中**查到了也建议在引用前 Grep 原始源码复核一次**（防止文档笔误）。
- 禁止凭旧版本经验（旧 Yarn 名、旧 Minecraft API、旧 Meteor API）生成代码。
- 所有文档中的「待源码确认」标记 = 尚未核实，禁止直接使用。
- 命令行速查工具（整合自 Mappings/）：

```powershell
node "d:\mcaddon\26.1.2\26.1.2-开发参考库\工具\查JARAPI.js" LocalPlayer          # 查类全路径+字段+方法
node "d:\mcaddon\26.1.2\26.1.2-开发参考库\工具\查JARAPI.js" LocalPlayer sendSys  # 类内搜方法
node "d:\mcaddon\26.1.2\26.1.2-开发参考库\工具\查JARAPI.js" --找 sendCommand     # 全局搜方法
```

---

## 四、最高频防踩坑速查（26.1.2 已改名/易错 API）

| 旧写法（Yarn/旧版，禁止） | 26.1.2 正确写法（Mojang 官方映射） |
|---------------------------|-------------------------------------|
| `ResourceLocation` | `net.minecraft.resources.Identifier` |
| `MinecraftClient` | `net.minecraft.client.Minecraft` |
| `ClientPlayerEntity` | `net.minecraft.client.player.LocalPlayer` |
| `ClientWorld` | `net.minecraft.client.multiplayer.ClientLevel` |
| `Text` / `MutableText` | `Component` / `MutableComponent` |
| `NbtCompound` | `net.minecraft.nbt.CompoundTag` |
| `World` / `ServerWorld` | `Level` / `ServerLevel` |
| `Formatting` | `net.minecraft.ChatFormatting` |
| `Box` | `net.minecraft.world.phys.AABB` |
| `Vec3d` | `net.minecraft.world.phys.Vec3` |
| `DrawContext` | `net.minecraft.client.gui.GuiGraphics` |
| `PlayerInventory` | `net.minecraft.world.entity.player.Inventory` |
| `ScreenHandler` | `net.minecraft.world.inventory.AbstractContainerMenu` |
| `ResourceKey#location()` | `ResourceKey#identifier()` |
| `Level#random`（字段） | `Level#getRandom()`（方法） |

完整清单见 `生成的索引文件\易错对照表-26.1.2.txt`（39 项）与 `工具\分类速查\`（15 个功能域类清单）。

---

## 五、中文命名规范

- 本资料库**文件夹名、分类名、Markdown 说明、解释文字、报告全部中文**。
- Java 类名 / 方法名 / 字段名 / 包名 / Packet 名 / Event 名 / Mixin Target / Registry ID / Meteor API 名**必须保留真实源码英文原名，禁止翻译代码标识符**。

---

## 六、版本事实依据

- Minecraft 原始源码来源：Fabric Loom 缓存的官方非混淆源码 JAR
  `minecraft-merged-83e224879c-26.1.2-sources.jar`（Mojang 官方发布，非反编译产物）
- Meteor 原始源码来源：meteor-client 26.1.2 源码包（与构建产物同版本）
- orbit 来源：meteordevelopment:orbit:0.2.4 sources.jar（Meteor 事件系统依赖）
- 版本互相印证：`Meteor原始源码\meteordevelopment\meteorclient\MeteorClient.java` 内
  `MOD_META.getVersion().getFriendlyString()` 与 fabric.mod.json 的 MC 依赖声明。