# PlayerUtils 机制

> `PlayerUtils` 是 addon 里「玩家实体相关审判/计算」的静态工具类，覆盖距离、视角可见性、角度计算、洞判定、潜在伤害估算、游戏模式/延迟读取。所有方法都是 `static`、无状态（除少量缓存字段），不依赖事件，直接 `PlayerUtils.xxx(...)` 调用。

## 概述

类：`meteordevelopment.meteorclient.utils.player.PlayerUtils`，`private PlayerUtils(){}` 禁止实例化，统一 `import static meteordevelopment.meteorclient.MeteorClient.mc;`。静态缓存字段：

```java
private static final double diagonal = 1 / Math.sqrt(2);
private static final Vec3 horizontalVelocity = new Vec3(0, 0, 0);  // 复用对象，经 mixin 改 XZ
private static final Color color = new Color();
```

---

## 1. 距离计算族（distanceTo / squaredDistance / isWithin / isWithinCamera / isWithinReach）

底层 `squaredDistance(...)` 用 `org.joml.Math.fma`（FMA 融合乘加）算三轴平方和：

```java
public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
    double f = x1 - x2, g = y1 - y2, h = z1 - z2;
    return org.joml.Math.fma(f, f, org.joml.Math.fma(g, g, h * h));
}
```

派生 API（真实名，重载覆盖 `Entity` / `BlockPos` / `Vec3` / 三个 double）：

- `distanceTo(...)` / `squaredDistanceTo(...)`：玩家到目标距离。
- `isWithin(...)`：`squaredDistanceTo(...) <= r * r`（避免开方）。
- `distanceToCamera(...)` / `squaredDistanceToCamera(...)`：到渲染相机（`mc.gameRenderer.getMainCamera().position()`）的距离；`isWithinCamera(...)` 同款判定。
- `isWithinReach(...)`：`squaredDistance(玩家眼位, 目标) <= mc.player.blockInteractionRange() * mc.player.blockInteractionRange()`（26.1.2 的方块交互距离来自 `blockInteractionRange()`）。

`distance(6 个 double)` 是纯通用两点距离（非玩家相关），`distanceTo(entity)` 用 `entity.getEyeHeight` 无关、直接 `entity.getX/Y/Z`。

## 2. 视角 / 可见性 / 角度

- `canSeeEntity(Entity)`：`mc.level.clip(new ClipContext(玩家眼位→实体脚位, COLLIDER, NONE, mc.player))` 是否 `MISS`，再检查眼位；**脚或眼任一无阻挡即 true**。
- `calculateAngle(Vec3 target)`：从玩家**眼睛位置**（`mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose())`）算出 yaw/pitch，返回 `float[]{yaw, pitch}`，角度 `Mth.wrapDegrees` 归一。
- `getHorizontalVelocity(double bps)`：按当前 `yaw`（若 `PathManagers.get().isPathing()` 则用 `getTargetYaw()`），依据 `mc.player.input.keyPresses` 的 `forward/backward/left/right` 合成水平速度；前后+左右同时按下时乘 `diagonal` (1/√2) 归一，返回复用对象 `horizontalVelocity`。
- `centerPlayer()`：把玩家 `x/z` 对齐到 `.5` 中心并 `mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(...))` 同步服务端。

## 3. 状态判定

- `isMoving()`：`mc.player.zza != 0 || mc.player.xxa != 0`。
- `isSprinting()`：`mc.player.isSprinting() && (zza != 0 || xxa != 0)`。
- `shouldPause(boolean ifBreaking, boolean ifEating, boolean ifDrinking)`：挖掘中 / 正在吃（`DataComponents.FOOD`）/ 正在喝（`PotionItem`）时返回 true——模块在自动化动作前用它判断「是否该暂停别的任务」。
- `isInHole(boolean doubles)`：遍历 `Direction.values()`（跳过 UP），若相邻方块 `getExplosionResistance() < 600` 判定为不安全；`doubles=true` 时额外检查斜对角是否挖空，`air < 2` 才算洞。**传 false 只判单洞，传 true 只判双洞（bed 洞）**。
- `isAlive()`：`mc.player.isAlive() && !mc.player.isDeadOrDying()`。
- `getTotalHealth()`：`getHealth() + getAbsorptionAmount()`。

## 4. 数值读取

- `getGameMode()`：经 `mc.getConnection().getPlayerInfo(uuid)` 拿 `PlayerInfo.getGameMode()`，可能为 null。
- `getPing()`：同上 `playerListEntry.getLatency()`（服务器延迟，非网络延迟；玩家离线/连接为 null 返回 0）。
- `getDimension()`：按 `mc.level.dimension().identifier().getPath()` 返回 `Dimension.Overworld/Nether/End`（`utils/world/Dimension.java` 枚举）。
- `getPlayerColor(Player, Color defaultColor)`：好友用 `Config.get().friendColor`；开 `useTeamColor` 且队伍色非白则用队伍色；否则返回默认色。

## 5. 潜在伤害估算（possibleHealthReductions）

```java
public static float possibleHealthReductions()                        // = (true, true)
public static float possibleHealthReductions(boolean entities, boolean fall)
```

- `entities` 分支遍历 `mc.level.entitiesForRendering()`：末影水晶 → `DamageUtils.crystalDamage`；拿剑玩家（非好友、5 格内）→ `DamageUtils.getAttackDamage`；下界 `BED_RULE.explodes()` 时遍历方块实体里的 `BedBlockEntity` → `DamageUtils.bedDamage`。
- `fall` 分支：`!Modules.get().isActive(NoFall.class) && fallDistance > 3` 且 `!EntityUtils.isAboveWater` 时 `DamageUtils.fallDamage`。
- 全程取最大值返回（`if (d > damageTaken) damageTaken = d`）。这是 `AutoLog`/自动吃金等模块的「即将受伤」判据来源。

## 运行链路

`PlayerUtils` 无事件、无生命周期——纯同步计算。调用点在模块 `onActivate` 或 `TickEvent` 里直接 `PlayerUtils.xxx()` 读取 `mc.player`/`mc.level` 当前快照。内部依赖：`PathManagers`（目标 yaw）、`Friends`/`Config`（颜色）、`DamageUtils`/`EntityUtils`（伤害）、`Dimension` 枚举。

## Addon 用法 / 介入点

```java
// 距离 + 范围内判断（不开方，性能好）
if (PlayerUtils.squaredDistanceTo(targetEntity) <= 4.5 * 4.5) { ... }
if (PlayerUtils.isWithin(targetEntity, 4.5)) { ... }

// 视野无障碍 + 算角度
if (PlayerUtils.canSeeEntity(target)) {
    float[] ang = PlayerUtils.calculateAngle(target.position());
}

// 是否处于单洞 / 是否在冲刺
if (PlayerUtils.isInHole(false)) { ... }
if (PlayerUtils.isSprinting()) { ... }

// 危险度估算（AutoLog 等）
float threat = PlayerUtils.possibleHealthReductions();

// 实时延迟 / 游戏模式
int ping = PlayerUtils.getPing();
GameType mode = PlayerUtils.getGameMode();
```

## 常见坑

1. **静态复用向量**：`getHorizontalVelocity` 返回共享的 `horizontalVelocity`，且经 `IVec3.meteor$setXZ` 改写；**不要持有引用跨 tick 使用**，用后立即消费。
2. **`getPing` 是服务器延迟且离线为 0**：判断在线不能只靠 `getPing() != 0`，要先 `mc.getConnection() != null`。网络延迟是另一概念（yiyiaddon 后端「服务器延迟/网络延迟」区分见项目约定）。
3. **`isWithinReach` 用 `blockInteractionRange()`**：交互实体用 `entityInteractionRange()`，别混用。
4. **`isInHole(doubles)` 判断基于 `getExplosionResistance() < 600`**：黑曜石(爆抗 1200)安全、床 impossible 场景会被判成洞；跨维度方块判定要 `Utils.canUpdate()` 先保护（该方法内已 `if (!Utils.canUpdate()) return false`）。
5. **`possibleHealthReductions` 计算开销较大**（遍历渲染实体 + 方块实体），别每 tick 无条件调用，应节流。
6. **`calculateAngle` 返回 `float[]` 且已 wrapDegrees**：不要重复 wrap。

## 源码依据

- `meteordevelopment/meteorclient/utils/player/PlayerUtils.java` —— 全部静态方法（距离族/可见性/计算角度/洞判定/伤害估算/延迟/维度/游戏模式）。
- `meteordevelopment/meteorclient/utils/entity/DamageUtils.java` —— `crystalDamage`/`getAttackDamage`/`bedDamage`/`fallDamage`（possibleHealthReductions 依赖）。
- `meteordevelopment/meteorclient/utils/entity/EntityUtils.java` —— `isAboveWater`。
- `meteordevelopment/meteorclient/utils/world/Dimension.java` —— `Overworld/Nether/End` 枚举。
- `meteordevelopment/meteorclient/mixininterface/IVec3.java` —— `meteor$set/meteor$setXZ`（静态向量复用依赖）。
- `meteordevelopment/meteorclient/pathing/PathManagers.java` —— `isPathing`/`getTargetYaw`（getHorizontalVelocity 依赖）。
- `meteordevelopment/meteorclient/systems/friends/Friends.java` / `systems/config/Config.java` —— `getPlayerColor` 依赖。