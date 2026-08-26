## yiyiaddon v1.1-beta4

> 🧪 Beta 测试版，欢迎反馈 bug  
> Minecraft 26.1.2 | Meteor Client 26.1.2-SNAPSHOT

---

### 📦 安装说明

1. 下载 `yiyiaddon1.1-beta4.jar` 放入 `.minecraft/mods/` 文件夹
2. 启动游戏，按 Right Shift 打开 Meteor 菜单
3. 在 `yiyiaddon 工具` 分类中找到所有模块
4. Baritone 已内置，无需额外安装

---

### 🔥 本次更新内容

**欢迎消息优化**
- 🎨 **分割线样式**：去掉边框，使用简洁的分割线包裹
- ✨ **护眼配色**：深青分割线 + 深绿强调 + 浅青链接
- 🔗 **可点击链接**：GitHub 仓库和 Bug 反馈链接可直接点击
- 📊 **用户统计**：显示排名和总用户数

**版本更新检查系统**
- 🔔 **自动检查**：每 24 小时自动检查一次更新
- 💬 **手动检查**：`.yiyiaddon check` 指令立即检查并反馈结果
- 🎯 **智能版本比较**：正确识别 beta 版本，测试版 < 正式版
- ⏱️ **即时反馈**：有更新/无更新/超时，都会立即提示
- 📝 **更新预览**：显示前 3 行更新内容
- 🚫 **跳过版本**：`.yiyiaddon skip` 跳过当前版本更新提示

---

### 🔧 修复与优化

- 修复版本比较逻辑（之前会把 1.1-beta3 标准化为 1.1，导致无法识别差异）
- 修复更新提示在后台线程无法显示的问题（改用主线程执行）
- 优化网络请求超时提示
- 统一更新提示显示逻辑

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
