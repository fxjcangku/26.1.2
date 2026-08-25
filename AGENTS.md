# AI 助手须知

本项目的开发规范与调试协议**全部写在** [YiyiaddonModule.java](src/main/java/com/example/addon/core/YiyiaddonModule.java) 的注释里。

动手之前必须先读完那个文件的顶部声明、第一部分和第二部分。它不是普通基类，是项目的规范载体。

## 四条铁律

1. **规范注释只增不删**：不得删除、精简或"优化" `YiyiaddonModule.java` 里的任何规范注释。新增规范追加到对应小节。
2. **证据驱动排查**：用户报 Bug 后，先按第二部分协议埋点取运行时证据，再下结论。禁止凭静态代码推测直接改。
3. **中文 + 日期归档**：调试产物一律进 `Diagnostics/`，文档中文命名并带 `YYYY-MM-DD-` 前缀。
4. **API 先查后写**：本项目用 Mojang 官方映射，**不是 Yarn**。写任何没在现有代码出现过的 `net.minecraft` API 之前，先用 `node Mappings/工具/查API.js <类名>` 确认它在 26.1.2 真实存在。

## 26.1.2 API 红线

对外版本 26.1.2 = 内部版本 1.21.11。这一版 Mojang 大规模改名，Yarn 映射已退役。下列写法**全部编译失败**：

```
✗ ResourceLocation   ✓ Identifier          ✗ MinecraftClient  ✓ Minecraft
✗ Text               ✓ Component           ✗ NbtCompound      ✓ CompoundTag
✗ World              ✓ Level               ✗ Vec3d            ✓ Vec3
✗ ClientPlayerEntity ✓ LocalPlayer         ✗ Hand             ✓ InteractionHand
```

完整对照见 `Mappings/易错对照表-26.1.2.txt`，用法见 `Mappings/说明.md`。

另外：`build.gradle.kts` 里没有 `mappings(loom.officialMojangMappings())` 是正确的——26.1 起官方不混淆，不需要重映射。不要补这行。

## 目录约定

```
Diagnostics/
├─ 工具/               调试监听服务.js（跨会话复用）
├─ 会话记录/
│  ├─ 进行中/          {日期}-{中文会话名}.md
│  └─ 已修复/          历史修复台账
└─ 运行日志/           .env 与 .ndjson 证据（不进 Git）
```

## 构建

需要 Java 21+。产物 `build/libs/yiyiaddon1.1-personal.jar`。

```powershell
$env:JAVA_HOME = (Get-Command java).Source | Split-Path | Split-Path
.\gradlew.bat buildPersonal
```
