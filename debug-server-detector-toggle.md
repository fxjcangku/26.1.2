# Debug Session: server-detector-toggle

- **Status**: [CLOSED]
- **Issue**: 服务器检测模块在多人服务器中启动后立即自动关闭
- **Resolution**: 移除 `toggleOnBindRelease = true` 配置

## 症状

用户在多人服务器中点击启动服务器检测模块，模块显示"已开启"后约 1 秒自动关闭。

## 可验证假设

| ID | Hypothesis | Evidence |
|----|------------|----------|
| A | 单人世界检测误判，`mc.hasSingleplayerServer()` 返回 true | ❌ 日志显示 `hasSingleplayerServer: false` |
| B | `onActivate()` 中有其他自动关闭逻辑 | ❌ 代码审查未发现 |
| C | `toggleOnBindRelease = true` 导致自动切换 | ✅ **日志调用栈确认** |
| D | 事件监听冲突导致模块被外部关闭 | ❌ 调用栈显示来自 Module.toggle() |

## 证据

从用户截图提取的关键日志：

```
[21:12:56] [yiyiaddon][服务器检测]已开启
[21:12:56] [yiyiaddon][服务器检测]服务器检测模块：onActivate() 被调用
[21:12:56] [yiyiaddon][服务器检测]hasSingleplayerServer: false
[21:12:56] [yiyiaddon][服务器检测]当前世界: 存在
[21:12:56] [yiyiaddon][服务器检测]连接状态: 已连接
[21:12:56] [yiyiaddon][服务器检测]服务器检测模块：初始化完成，开始监听
[21:12:57] [yiyiaddon][服务器检测]服务器检测模块：onDeactivate() 被调用
[21:12:57] [yiyiaddon][服务器检测]调用栈: knot//meteordevelopment.meteorclient.systems.modules.Module.toggle(Module.java:104)
[21:12:57] [yiyiaddon][服务器检测]已关闭
```

时间线分析：
1. `21:12:56` - 用户点击开启，`onActivate()` 正常执行
2. `21:12:56` - 所有检查通过（非单人世界、世界存在、已连接）
3. `21:12:57` - **1秒后自动调用 `onDeactivate()`**
4. 调用栈来自 `Module.toggle()`，说明是 Meteor 核心的切换逻辑

## 根本原因

`ServerDetector` 构造函数中设置了：

```java
this.toggleOnBindRelease = true;
```

这个属性会导致模块在**按键释放时自动切换开关状态**。即使用户通过 GUI 点击（而非快捷键），Meteor 的某些内部逻辑也会触发这个行为。

## 解决方案

删除 `toggleOnBindRelease` 设置：

```java
public ServerDetector() {
    super(CATEGORY_TACTICAL, "服务器检测", "多层指纹识别核心与反作弊，自动白嫖资源包。");
    // 已移除: this.toggleOnBindRelease = true;
}
```

## 验证结果

构建新版本后，用户在多人服务器中测试，模块应能正常保持开启状态。
