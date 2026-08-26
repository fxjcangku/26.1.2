## yiyiaddon v1.1-beta3

> 🧪 Beta 测试版，欢迎反馈 bug  
> Minecraft 26.1.2 | Meteor Client 26.1.2-SNAPSHOT

---

### 📦 安装说明

1. 下载 `yiyiaddon1.1-beta3.jar` 放入 `.minecraft/mods/` 文件夹
2. 启动游戏，按 Right Shift 打开 Meteor 菜单
3. 在 `yiyiaddon 工具` 分类中找到所有模块
4. Baritone 已内置，无需额外安装

---

### 🔥 本次更新内容

**欢迎消息优化**
- ✨ **护眼配色**：统一深青边框 + 深绿强调 + 浅青链接，降低视觉疲劳
- 🔗 **GitHub 链接**：新增可点击的仓库链接和 Bug 反馈入口
- **加粗显示**：关键数字、时间单位、统计信息全部加粗突出
- 📊 **活跃统计**：24h 活跃用户数 + 最近上线玩家实时显示

**版本更新提示**
- 🔔 **智能检测**：自动区分测试版（beta）和正式版，只提示正式版更新
- 🎨 **样式统一**：更新播报采用与欢迎消息相同的护眼配色模板
- 📝 **更新预览**：显示前 3 行更新内容，方便快速了解新版本

---

### 🔧 修复与优化

- 修复链接无法点击的问题（使用正确的 `ClickEvent.OpenUrl` API）
- 修复 GitHub API 版本检测逻辑（通过 `prerelease` 字段过滤测试版）
- 优化消息显示宽度，减少文本溢出

---

## 环境要求

| 依赖 | 版本 |
|------|------|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.3 |
| Meteor Client | 26.1.2-SNAPSHOT |
| Java | 25 |

💬 Discord：https://discord.gg/vwrRCtET  
🔗 GitHub：https://github.com/fxjcangku/26.1.2
