# Mappings - Fabric 26.1.2 API 速查目录

## 快速开始

```powershell
# 1. 确保 Gradle 已生成 Minecraft JAR
.\gradlew.bat classes

# 2. 生成类名索引
node Mappings\工具\从MinecraftJAR生成索引.js

# 3. 查询 API
node Mappings\工具\查JARAPI.js Minecraft
node Mappings\工具\查JARAPI.js LocalPlayer sendSystem
node Mappings\工具\查JARAPI.js --找 sendCommand
```

## 目录结构

```
Mappings/
├─ README.md                         快速入门（本文件）
├─ 工具/                             开发工具
│  ├─ 从MinecraftJAR生成索引.js     从本地 JAR 生成索引
│  ├─ 查JARAPI.js                   查询类和方法
│  ├─ 生成分类速查表.js             生成分类速查表
│  └─ 验证易错API.js                验证旧名称对照
├─ 文档/                             详细文档
│  ├─ 说明.md                       详细使用说明
│  └─ 来源与版本记录-26.1.2.md     官方来源和版本事实
├─ 生成的索引文件/                   ⚠️ 由工具自动生成，不要手动编辑
│  ├─ 类名索引-26.1.2.txt           10208 个 net.minecraft 类
│  ├─ 简名对照-26.1.2.txt           9677 个简名映射
│  └─ 易错对照表-26.1.2.txt         Yarn/旧名对照
└─ 分类速查/                         ⚠️ 由工具自动生成，不要手动编辑
   ├─ 01-客户端与玩家.txt
   ├─ 02-文本与聊天.txt
   ├─ 03-物品与背包.txt
   ├─ 04-方块与世界.txt
   ├─ 05-实体.txt
   ├─ 06-网络数据包.txt
   ├─ 07-坐标与数学.txt
   ├─ 08-注册表与资源标识.txt
   ├─ 09-NBT标签.txt
   ├─ 10-GUI界面.txt
   ├─ 11-渲染.txt
   ├─ 12-声音.txt
   ├─ 13-指令系统.txt
   ├─ 14-顶层常用类.txt
   └─ 99-其他未分类.txt
```

## 工具说明

| 工具 | 用途 | 依赖 |
|---|---|---|
| `从MinecraftJAR生成索引.js` | 从本地 Minecraft JAR 生成类名索引和简名对照表 | `.gradle/loom-cache` 中的 JAR |
| `查JARAPI.js` | 查询类、方法和字段，支持全局搜索 | 类名索引 + JAR |
| `生成分类速查表.js` | 按功能域生成分类速查文件 | 类名索引 |
| `验证易错API.js` | 生成易错 API 对照表 | 类名索引 |

## 使用方法

### 初始化（clone 之后第一次）

```powershell
# 1. 让 Loom 准备 Minecraft JAR
.\gradlew.bat classes

# 2. 生成索引
node Mappings\工具\从MinecraftJAR生成索引.js

# 3. 生成分类速查表（可选）
node Mappings\工具\生成分类速查表.js

# 4. 生成易错对照表（可选）
node Mappings\工具\验证易错API.js
```

### 日常查询

```powershell
# 查类和方法
node Mappings\工具\查JARAPI.js Minecraft
node Mappings\工具\查JARAPI.js LocalPlayer sendSystem

# 全局搜方法
node Mappings\工具\查JARAPI.js --找 sendCommand
```

## 为什么要这样设计

### Minecraft 26.1 的变化

Minecraft 26.1 起，Mojang 官方发布**非混淆代码**：

- ✅ 类名、方法名、字段名直接编译进 JAR
- ❌ 不再提供传统的 `client_mappings.txt` / `server_mappings.txt`
- ❌ Yarn 映射退出主线开发流程

### Fabric 26.1 的开发方式

- 使用 `net.fabricmc.fabric-loom` 插件
- **不配置** `mappings(loom.officialMojangMappings())`
- 直接针对官方非混淆 JAR 编译
- 使用普通依赖配置（`implementation`），不是 `modImplementation`

### 本目录的定位

不是传统 Yarn/Intermediary 映射仓库，而是：

1. **本地 API 速查工具**：从实际 JAR 生成索引
2. **迁移辅助资料**：记录 Yarn/旧名 → 官方名对照
3. **版本事实来源**：记录 Fabric 官方文档和版本信息

## 常见问题

### 如何查询某个类是否存在？

```powershell
# 方式 1：查看类名索引
cat Mappings\生成的索引文件\类名索引-26.1.2.txt | Select-String "Identifier"

# 方式 2：使用查询工具
node Mappings\工具\查JARAPI.js Identifier
```

### 如何查找某个方法在哪个类里？

```powershell
node Mappings\工具\查JARAPI.js --找 sendCommand
```

### 为什么有些类找不到？

可能原因：

1. **改名了**：查看 `生成的索引文件/易错对照表-26.1.2.txt`
2. **只存在于旧版本**：26.1.2 可能已移除
3. **拼写错误**：区分大小写

常见改名：

| 旧名称（Yarn） | 26.1.2 官方名 |
|---|---|
| `ResourceLocation` | `Identifier` |
| `MinecraftClient` | `Minecraft` |
| `ClientPlayerEntity` | `LocalPlayer` |
| `Text` | `Component` |
| `World` | `Level` |
| `Vec3d` | `Vec3` |

## 权威来源

- [Fabric 26.1.2 文档](https://docs.fabricmc.net/26.1.2/)
- [Fabric Loom 文档](https://docs.fabricmc.net/26.1.2/develop/loom/)
- [Minecraft 26.1 迁移指南](https://docs.fabricmc.net/26.1.2/develop/porting/)
- [Fabric API 26.1 改名表](https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api)
- [Fabric 示例项目 26.1](https://github.com/FabricMC/fabric-example-mod/tree/26.1)
- [Mojang 官方：取消混淆](https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition)

## 注意事项

1. **Minecraft 官方命名** 和 **Fabric API 改名** 是两套变化
2. 不要照搬旧教程或 AI 记忆中的 Yarn 名称
3. 有疑问时先用工具查询，不要凭记忆写代码
4. 本地 JAR 是唯一事实来源
5. `生成的索引文件/` 和 `分类速查/` 目录由工具自动生成，不要手动编辑
