<div align="center">

# yiyiaddon v1.0

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-brightgreen?style=for-the-badge&logo=minecraft)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3-orange?style=for-the-badge)
![Meteor](https://img.shields.io/badge/Meteor_Client-26.1.2--SNAPSHOT-blue?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-red?style=for-the-badge&logo=openjdk)

**为 Meteor Client 量身打造的增强插件**  
**专注全中文界面 · 自动化工具 · 智能农场**

[📥 下载](https://github.com/fxjcangku/26.1.2/releases) · [📖 文档](#-使用说明) · [💬 Discord](https://discord.gg/vwrRCtET) · [🐛 反馈问题](https://github.com/fxjcangku/26.1.2/issues)

---

</div>

## ✨ 核心特性

### 🌐 完整中文化

- **Meteor Client 界面全中文** — 模块、设置、帮助信息、按钮、提示全部汉化
- **Baritone 导航系统全中文** — 指令、帮助文档、设置项、聊天反馈全面本地化
- **统一消息格式** — 所有插件消息使用 `[yiyiaddon]` 前缀，清晰识别

### 🚜 自动物流农场

全自动收割、补种、卸货、补货循环系统，解放双手：

- **10 种作物支持**：小麦、甜菜、土豆、胡萝卜、地狱疣、竹子、甘蔗、仙人掌、南瓜、西瓜
- **蛇形巡逻** — Baritone 自动走位，覆盖整片农田
- **智能物流** — 自动卸货到指定箱子，自动从补货箱取种子
- **异常自愈** — 种子不足降级"只收不种"，补货后自动恢复
- **时运防爆锁** — 工具耐久不足自动切空手，保护贵重工具
- **可视化辅助** — 农田雷达、边界外框、水源辐射范围渲染

**指令**：
```
.nongchang set 起点/终点/卸货箱/补货箱   绑定农场锚点
.nongchang status                      查看配置详情
.nongchang clear                       一键清空所有锚点
```

### 📚 Baritone 中文指令手册

内置交互式指令说明面板，打开模块即可查看：
- 常用指令速查（goto、mine、farm、follow 等）
- 参数格式详解
- 实用示例
- 配置建议

---

## 📦 安装

1. 下载 `yiyiaddon1.0.jar`
2. 放入 `.minecraft/mods/` 文件夹
3. 启动游戏，按 `Right Shift` 打开 Meteor 菜单
4. 在 `yiyiaddon 工具` 分类中找到所有模块

**注意**：
- Baritone 已内置在插件中，**无需额外安装** Baritone jar
- 需要 Fabric Loader 0.19.3 和 Meteor Client 26.1.2-SNAPSHOT

---

## 🎮 使用说明

### 中文化模块

打开 Meteor 菜单后：
1. 找到 `yiyiaddon 工具` 分类
2. 启用 `Meteor 与 Baritone 中文翻译`
3. 所有界面立即切换为中文

### 自动物流农场

**准备**：
1. 建好农田（耕地）
2. 放置卸货箱（接漏斗）和补货箱（装种子）
3. 在配置页面勾选要种的作物
4. 用指令绑定四个锚点

**运行**：
- 主手拿时运工具（可选）
- 背包准备好种子
- 开启模块，站在农田附近
- 脚本会自动收割、播种、卸货、补货循环

**推荐配置**：
- 卸货阈值：20 组
- 种子安全库存：3 组
- BPT 限速：10（每 tick 操作格子数）
- 收割距离：4 格（原版上限约 4.5 格）

---

## ⚙️ 技术特性

- **纯客户端模组** — 无需服务端支持，单人/多人均可使用
- **标准交互包** — 使用 Minecraft 原生交互协议，兼容性强
- **分帧扫描** — 512 格/tick，大农场不卡顿
- **状态机设计** — 六状态循环，看门狗防卡死
- **容器同步机制** — 正确处理 26.1.2 的容器交互，杜绝幽灵物品

---

## 🔗 链接

- **GitHub**：[https://github.com/fxjcangku/26.1.2](https://github.com/fxjcangku/26.1.2)
- **Discord**：[https://discord.gg/vwrRCtET](https://discord.gg/vwrRCtET)
- **问题反馈**：[GitHub Issues](https://github.com/fxjcangku/26.1.2/issues)

---

## 🔗 参考与致谢

- **Meteor Client 模板**：[MeteorCommunity/example-addon](https://github.com/MeteorCommunity/example-addon)
- **Meteor Client**：[https://meteorclient.com](https://meteorclient.com)
- **Baritone**：[cabaletta/baritone](https://github.com/cabaletta/baritone)

---

## 📝 更新日志

### v1.0（2026-08-24）

🎉 **首个公开版本**

**新增**：
- Meteor Client 完整中文化
- Baritone 导航系统完整中文化
- 自动物流农场模块（10 种作物）
- Baritone 中文指令手册

**优化**：
- 统一消息格式和配色
- 模块说明面板重构
- 指令输出美化

---

## 📄 许可

本项目仅供学习交流使用。

**环境要求**：
| 依赖 | 版本 |
|------|------|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.3 |
| Meteor Client | 26.1.2-SNAPSHOT |
| Java | 25 |

---

<div align="center">

**让 Minecraft 更智能，让游戏更轻松**

Made with ❤️ by yiyiaddon team

</div>
