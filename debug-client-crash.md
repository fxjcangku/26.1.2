# Debug Session: client-crash

- **Status**: [CLOSED]
- **Issue**: 客户端启动后崩溃，报告文件 `build/libs/错误报告-2026-8-25_20.45.12.zip`
- **Resolution**: 已定位为 CIT Resewn 兼容性问题，非 addon 代码导致

## 症状

客户端启动约 2.5 秒后崩溃。

## 可验证假设

| ID | Hypothesis | Evidence |
|----|------------|----------|
| A | `MinerFSM` 的状态转换或空对象访问触发崩溃 | ❌ 崩溃栈未涉及 addon 代码 |
| B | Fabric、Meteor、Minecraft 版本或映射不兼容 | ❌ 其他模组正常加载 |
| C | 打包 JAR 缺少依赖、资源或嵌入的 Baritone | ❌ JAR 构建正常 |
| D | Mixin 注入失败或方法签名不匹配 | ❌ 未见 Mixin 错误 |
| E | CIT Resewn 过早访问未绑定的物品组件 | ✅ **崩溃栈确认** |

## 证据

从崩溃报告提取的关键栈：

```
java.lang.NullPointerException: Components not bound yet
at net.minecraft.core.Holder$Reference.components(Holder.java:278)
at net.minecraft.world.item.ItemStack.<init>(ItemStack.java:266)
at net.minecraft.world.item.Item.getDefaultInstance(Item.java:681)
at net.minecraft.client.renderer.item.ItemModelResolver.citresewn$canReplaceItemModel(ItemModelResolver.java:593)
```

## 结论

**根本原因**：CIT Resewn Continuation 1.2.2-fork.12+26.1.2 在渲染线程中过早调用 `Item.getDefaultInstance()`，此时物品组件尚未绑定完成。

**解决方案**：移除或更新 CIT Resewn 及 CIT Resewn Defaults 模组。
