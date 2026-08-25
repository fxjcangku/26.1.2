# AI 助手须知

本项目的开发规范与调试协议**全部写在** [YiyiaddonModule.java](src/main/java/com/example/addon/core/YiyiaddonModule.java) 的注释里。

动手之前必须先读完那个文件的顶部声明、第一部分和第二部分。它不是普通基类，是项目的规范载体。

## 三条铁律

1. **规范注释只增不删**：不得删除、精简或"优化" `YiyiaddonModule.java` 里的任何规范注释。新增规范追加到对应小节。
2. **证据驱动排查**：用户报 Bug 后，先按第二部分协议埋点取运行时证据，再下结论。禁止凭静态代码推测直接改。
3. **中文 + 日期归档**：调试产物一律进 `Diagnostics/`，文档中文命名并带 `YYYY-MM-DD-` 前缀。

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
