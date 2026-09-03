<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:0d1117,100:00d9ff&height=180&section=header&text=yiyiaddon&fontSize=56&fontColor=ffffff&animation=fadeIn" alt="yiyiaddon" width="100%" />

<img src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=minecraft%20automated%20mining%20cinematic%20scene%2C%20futuristic%20holographic%20mining%20drill%2C%20glowing%20diamond%20ore%20in%20dark%20cave%2C%20cyan%20blue%20neon%20tech%20dashboard%2C%20professional%20high%20quality%20digital%20art&image_size=landscape_16_9" alt="yiyiaddon 自动化脚本" width="860" />

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-brightgreen?style=for-the-badge&logo=mojangstudios&logoColor=white)
![Fabric](https://img.shields.io/badge/Fabric_Loader-0.19.3-orange?style=for-the-badge)
![Meteor](https://img.shields.io/badge/Meteor_Client-26.1.2-blue?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-red?style=for-the-badge&logo=openjdk&logoColor=white)
![Version](https://img.shields.io/badge/Version-v1.3-9cf?style=for-the-badge)

**面向 Minecraft 26.1.2 的 Meteor Client 专业自动化扩展**

**状态机驱动的全流程自动化 · 全量中文本地化 · 移动传送一体化**

[![下载](https://img.shields.io/github/downloads/fxjcangku/26.1.2/total?style=for-the-badge&logo=github&label=Downloads&color=2ea44f)](https://github.com/fxjcangku/26.1.2/releases)
[![Stars](https://img.shields.io/github/stars/fxjcangku/26.1.2?style=for-the-badge&logo=github&label=Stars)](https://github.com/fxjcangku/26.1.2/stargazers)

[📥 立即下载](https://github.com/fxjcangku/26.1.2/releases/latest) · [💬 Discord 社区](https://discord.gg/vwrRCtET) · [🐛 反馈 Bug](https://github.com/fxjcangku/26.1.2/issues)

</div>

---

## 🧭 项目定位

**yiyiaddon** 是一个运行在 [Meteor Client](https://meteorclient.com) 之上的**专业自动化扩展**。

我们把重复性的游戏操作——挖矿、耕种、交易、附魔、移动与传送——工程化为**可配置、可观测、可复用**的自动化流程：

- ✅ **状态机架构**：每个流程由独立状态机驱动，支持断点恢复、异常自愈与看门狗保护，稳定挂机不卡死
- ✅ **完整执行反馈**：每个环节都有进度播报与结果播报，随时知道「正在做什么、做成了没有」
- ✅ **三层安全校验**：碰撞容纳、危险排除、服务端验证三级判据，落点永远真实可站立
- ✅ **纯客户端运行**：不需要任何服务端插件，单人 / 多人通用
- ✅ **持续迭代**：跟随 26.1.2 版本持续更新，有 Bug 必修复

---

## ⭐ 三大核心体系

### ⛏️ 自动化生产线

覆盖资源采集到加工的全链路，全流程无需手动干预。

<img src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=minecraft%20automated%20tunnel%20mining%2C%20glowing%20diamond%20ore%20vein%2C%20baritone%20navigation%20path%20hologram%20overlay%2C%20cyberpunk%20neon%20cyan%20highlights%2C%20professional%20game%20hud&image_size=landscape_16_9" alt="自动挖矿" width="760" />

| 能力 | 说明 |
| --- | --- |
| 🎯 目标选择器 | 可视化选择要挖的矿物，支持保留 / 丢弃白名单 |
| 🧭 Baritone 寻路 | 自动寻路到矿点，安全站位，无需手动操作 |
| 🎒 背包管理 | 自动丢弃无用方块、切换工具、保护时运装备 |
| 🛡️ 耐久预警 | 工具耐久不足自动切换副手经验修补，提示具体工具名 |
| 📦 潜影盒打包 | 背包满自动装盒换盒，长时间挂机不中断 |

### 🌀 移动与传送

三模式安全传送 + 多模式飞行，跨地形移动一体化解决方案。

| 模式 | 说明 |
| --- | --- |
| 🏔️ **TP地面** | 一键回到头顶真正的露天地表，洞穴脱身必备 |
| 🧱 **TP穿墙** | 捕获真实三维准星方向，智能搜索落点：穿墙、穿建筑、穿山体，前方无墙也能方向赶路，落点被占自动就近修正 |
| 🎯 **TP坐标** | 定点传送或 `.tp X Y Z` 指令直达配置坐标 |
| 🛡️ **回弹验证** | 每次位移都经服务端权威包验证，被修正立即播报偏差 |
| ✈️ **多模式飞行** | 多套移动方案自适应服务器环境 |

### 🌐 完整汉化

把 Meteor 和 Baritone 的所有界面、指令、提示，全部翻译成中文。

<img src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=chinese%20localization%20software%20interface%2C%20minecraft%20game%20menu%20translated%20to%20chinese%2C%20clean%20modern%20ui%20design%2C%20bilingual%20text%2C%20cyan%20neon%20accents&image_size=landscape_16_9" alt="完整汉化" width="760" />

| 覆盖范围 | 翻译项数 | 覆盖率 |
| --- | --- | --- |
| Meteor 模块 / 设置 | 700+ | 100% |
| Baritone 指令 / 设置 | 210+ | 100% |
| 聊天消息 / 提示 | 300+ | 100% |

> 无需配置，启用「翻译」模块后，整个界面立即切换为中文。

---

## 🧩 功能模块一览

### 🚜 自动化模块

| 模块 | 功能 |
| --- | --- |
| ⛏️ **自动挖矿** | 自动寻矿、开采、背包管理全流程 |
| 🌾 **自动农场** | 10 种作物自动种植收割，蛇形巡逻 + 智能物流 |
| 🦴 **自动骨粉** | 范围催熟作物，附带 ESP 高亮 |
| 📖 **附魔交易所** | 自动刷图书管理员，命中目标附魔自动购买 |
| 🤝 **自动村民交易** | 自动与村民交易，支持原地 / 寻路 / 多任务 |
| ✨ **扩展附魔** | 经验获取 → 定向附魔 → 洗练仓储全闭环 |
| 🔑 **自动登录** | 自动注册、登录、断线重连、进服执行指令 |
| ⚡ **自动断线** | 应急断线，配合自动化流程使用 |

### 🌀 移动与传送

| 模块 | 功能 |
| --- | --- |
| 🏔️ **TP地面** | 一键回地表，洞穴脱身 |
| 🧱 **TP穿墙** | 三维准星智能落点，穿墙穿山 + 方向赶路 |
| 🎯 **TP坐标** | 定点传送，支持 `.tp X Y Z` 指令 |
| ✈️ **飞行** | 多模式移动方案，自动适应服务器环境 |

### 🧰 辅助模块

| 模块 | 功能 |
| --- | --- |
| 📦 **自动箱子** | 扫描附近容器，按目标清单自动取物 |
| 🔍 **ID 识别** | 识别手持物品 / 实体，生成完整物品身份 |
| 🗂️ **ID 配置管理** | 管理已识别 / 手动添加的物品 ID |

### 🛠️ 工具模块

| 模块 | 功能 |
| --- | --- |
| 🌐 **完整汉化** | Meteor / Baritone 界面全中文化 |
| 📖 **Baritone 指令手册** | 内置中文指令速查表 |
| 📖 **Meteor 指令手册** | 内置中文指令速查表 |
| 🎨 **界面主题** | 一键切换界面与 HUD 配色 |
| 📊 **用户统计** | 实时查看扩展使用人数 |

### 🛡️ 服务器适配模块

| 模块 | 功能 |
| --- | --- |
| 🧱 **防踢** | 智能发包与通信管理 |
| 🔍 **服务器检测** | 识别服务器类型并自动调整策略 |
| 👁️ **管理员检测** | 识别旁观 / 隐身管理员 |
| ⛏️ **发包秒破** | 快速破坏方块 |

---

## 🚀 快速开始

### 前置要求

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.3+ |
| Meteor Client | 26.1.2 |
| Java | 25+ |

### 安装步骤

1. **下载**：前往 [Release 页面](https://github.com/fxjcangku/26.1.2/releases/latest) 下载 `yiyiaddon1.3.jar`
2. **放入 mods**：复制到 `.minecraft/mods/` 目录
3. **启动游戏**：选择 Fabric 实例，确认 Meteor 已加载（Baritone 已内置）
4. **打开菜单**：按 `Right Shift`，即可在分类中看到「自动化 / 移动传送 / 辅助 / 工具 / 服务器适配」模块

---

## 💬 加入社区

有任何问题、建议或想交流自动化玩法，欢迎加入 Discord：

[![Discord](https://img.shields.io/badge/Discord-%E7%AB%8B%E5%8D%B3%E5%8A%A0%E5%85%A5-7289da?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/vwrRCtET)

---

## 🐛 有 Bug 请反馈

如果遇到崩溃、功能异常或翻译错误，请到 [Issues](https://github.com/fxjcangku/26.1.2/issues) 提交，尽量附带：

- Minecraft / Fabric / Meteor / yiyiaddon 版本号
- 复现步骤
- `.minecraft/logs/latest.log` 日志

我会尽快修复。

---

## 📝 更新日志

### v1.3

- 🌀 **全新「传送」模块**：TP地面 / TP穿墙 / TP坐标 三模式，独立快捷键一键触发
- 🧱 TP穿墙基于真实三维准星射线智能搜索落点：穿墙穿山、方向赶路一体，落点被占自动就近修正
- 🛡️ 每次位移均经服务端权威包验证，被修正立即播报实际偏差
- ⌨️ 快捷键在模块未开启时智能提醒；数值配置统一为加减按钮 + 输入框
- 🐛 修复事件双重订阅等潜在稳定性问题

### v1.2

- ⛏️ **自动挖矿**：目标选择器、背包管理、潜影盒打包全面完善
- 🌐 **完整汉化**：翻译覆盖进一步补齐
- 📦 新增「自动箱子」「ID 识别」「ID 配置管理」三大辅助模块

---

## ⚠️ 免责声明

- 本项目为第三方客户端脚本扩展，与 Mojang / Minecraft 官方无关
- 请遵守服务器规则，谨慎使用自动化功能
- 使用本扩展产生的一切后果由使用者自行承担

---

<div align="center">

**让 Minecraft 更智能，让游戏更轻松**

⭐ 如果觉得好用，请点个 Star 支持我继续迭代

</div>