# AI 助手须知

> 本项目所有开发规范的**唯一权威正本**在 `convention/YiyiaddonConvention.java` 的中文注释里，动手前必须先完整读完那个文件的全部注释，不允许只挑代码相关段落跳读。

## 五条铁律

1. **规范注释只增不删**：不得删除、精简或改写任何一条既有规范；新增规范追加到对应章节。
2. **证据驱动排查**：用户报 Bug 后，先按规范第六章协议埋点取运行时证据再下结论，禁止凭静态代码推测直接改业务逻辑。
3. **中文 + 日期归档**：调试产物一律进 `Diagnostics/`，文档中文命名并带 `YYYY-MM-DD-` 前缀。
4. **API 先查后写**：本项目用 Mojang 官方映射，不是 Yarn。写任何没在现有代码出现过的 `net.minecraft` API 前，先 `node Mappings/工具/查API.js <类名>` 确认它在 26.1.2 真实存在。
5. **分类铁律**：代码只能放对分类的目录，新功能必须新建独立英文文件夹（包），不得乱塞进现有不相关目录。文件夹/包名英文，注释与内容中文。

## 唯一规范正本

```
src/main/java/com/example/addon/convention/YiyiaddonConvention.java
```

该文件包含十章：AI 声明与铁律、对话偏好、代码分类铁律、后台 API 与账号、模块开发规范、运行时缺陷排查协议、26.1.2 API 规范、混淆发布规范、构建命令、GitHub 仓库结构与 Release 模板。

## 关键速查

- **后台登录**：账号 `admin`；密码通过 `wrangler secret` 设置 `ADMIN_PASSWORD`（禁止写死在代码里）。
- **构建**：JDK 25（不是 21）。产物 `yiyiaddon1.1-personal.jar`。

```powershell
$env:JAVA_HOME = (Get-Command java).Source | Split-Path | Split-Path
.\gradlew.bat buildPersonal
```

- **目录约定**：诊断产物进 `Diagnostics/`（工具 / 会话记录 / 运行日志），映射速查进 `Mappings/`，第三方参考源码进 `Reference/`。
- **映射只回答「类/方法存在吗」**；想知道项目怎么组织代码（Mixin 注入点、包拦截、容器交互）查 `Reference/`，只读思路，禁止复制代码或嵌套打包第三方 JAR。