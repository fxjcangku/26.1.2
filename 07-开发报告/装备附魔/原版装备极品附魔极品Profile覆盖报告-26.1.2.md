# 原版装备极品附魔 · 极品 Profile 覆盖报告（26.1.2）

日期：2026-09-01
报告文件：gear-profile-coverage-26.1.2.md

---

## 1. 原有 Profile（11 件，未改动）
钻石剑（锋利/亡灵/节肢 3 路线）、钻石镐/斧/锹/锄（时运/精准 各 2 路线）、弓（力量）、弩（多重/穿透）、钻石头盔/胸甲/护腿/靴子（四保护各 4 路线）。

## 2. 新增 Profile（12 件）

| 装备 | 类别 | 方案 |
|------|------|------|
| 下界合金镐 netherite_pickaxe | 工具 | 时运 / 精准采集 |
| 下界合金斧 netherite_axe | 工具 | 时运 / 精准采集 |
| 下界合金锹 netherite_shovel | 工具 | 时运 / 精准采集 |
| 下界合金锄 netherite_hoe | 工具 | 时运 / 精准采集 |
| 下界合金剑 netherite_sword | 武器 | 锋利 / 亡灵杀手 / 节肢杀手 |
| 下界合金矛 netherite_spear | 武器 | 锋利 / 亡灵杀手 / 节肢杀手 |
| 三叉戟 trident | 武器 | 忠诚 / 激流 |
| 重锤 mace | 武器 | 破甲 / 致密 |
| 下界合金头盔 netherite_helmet | 护甲 | 四保护 |
| 下界合金胸甲 netherite_chestplate | 护甲 | 四保护（含荆棘） |
| 下界合金护腿 netherite_leggings | 护甲 | 四保护 |
| 下界合金靴子 netherite_boots | 护甲 | 四保护（含摔落/深海） |

Profile 总数：11 + 12 = **23 件**（覆盖 75 件官方装备中的核心极品装备）。

## 3. 每个方案的目标与可达性

### 下界合金工具（enchantability=15，附魔台 cost 上限 43）
| 附魔 | 目标 | 最高 | 附魔台可达 | 铁砧升级 |
|------|------|------|-----------|----------|
| 效率 efficiency | 5 | 5 | ✅ 可达（minCost 41 ≤ 43） | 无需 |
| 时运 fortune | 3 | 3 | ✅ 可达 | 无需 |
| 精准采集 silk_touch | 1 | 1 | ✅ 可达 | 无需 |
| 耐久 unbreaking | 3 | 3 | ✅ 可达 | 无需 |

> 关键差异：下界合金（enchantability=15）效率 V **附魔台直出**；钻石（enchantability=10）效率 V 需铁砧 IV+IV→V。

### 下界合金剑 / 下界合金矛（enchantability=15）
| 附魔 | 目标 | 最高 | 附魔台可达 | 铁砧升级 |
|------|------|------|-----------|----------|
| 锋利/亡灵/节肢 sharpness/smite/bane | 4 | 5 | ✅ 可达（IV，minCost 34 ≤ 43） | V 需 IV+IV |
| 突进 lunge（仅矛） | 3 | 3 | ✅ 可达 | 无需 |
| 横扫 sweeping_edge（仅剑） | 3 | 3 | ✅ 可达 | 无需 |
| 抢夺 looting | 3 | 3 | ✅ 可达 | 无需 |
| 火焰附加 fire_aspect | 2 | 2 | ✅ 可达 | 无需 |
| 击退 knockback | 2 | 2 | ✅ 可达 | 无需 |
| 耐久 unbreaking | 3 | 3 | ✅ 可达 | 无需 |

### 三叉戟（enchantability=1，附魔台 cost 上限 36）
| 附魔 | 目标 | 最高 | 附魔台可达 | 铁砧升级 |
|------|------|------|-----------|----------|
| 忠诚 loyalty | 3 | 3 | ✅ 可达 | 无需 |
| 激流 riptide | 3 | 3 | ✅ 可达 | 无需 |
| 穿刺 impaling | 5 | 5 | ✅ 可达 | 无需 |
| 引雷 channeling | 1 | 1 | ✅ 可达 | 无需 |
| 耐久 unbreaking | 3 | 3 | ✅ 可达 | 无需 |

### 重锤（enchantability=15，附魔台 cost 上限 43）
| 附魔 | 目标 | 最高 | 附魔台可达 | 铁砧升级 |
|------|------|------|-----------|----------|
| 破甲 breach | 4 | 4 | ✅ 可达（minCost 42 ≤ 43） | 无需 |
| 致密 density | 5 | 5 | ✅ 可达 | 无需 |
| 耐久 unbreaking | 3 | 3 | ✅ 可达 | 无需 |

### 下界合金护甲（enchantability=15）
| 附魔 | 目标 | 最高 | 附魔台可达 | 铁砧升级 |
|------|------|------|-----------|----------|
| 四保护 protection/fire/blast/projectile | 4 | 4 | ✅ 可达 | 无需 |
| 荆棘 thorns（胸甲） | 3 | 3 | ⚠️ 仅 II（minCost 50 > 43） | III 需 II+II |
| 水下呼吸 respiration（头） | 3 | 3 | ✅ 可达 | 无需 |
| 水下速掘 aqua_affinity（头） | 1 | 1 | ✅ 可达 | 无需 |
| 摔落缓冲 feather_falling（靴） | 4 | 4 | ✅ 可达 | 无需 |
| 深海探索者 depth_strider（靴） | 3 | 3 | ✅ 可达 | 无需 |
| 耐久 unbreaking | 3 | 3 | ✅ 可达 | 无需 |

## 4. 互斥关系
- 剑/矛：锋利 ↔ 亡灵 ↔ 节肢（三选一，exclusiveWith 互斥方案）
- 工具：时运 ↔ 精准采集
- 三叉戟：忠诚 ↔ 激流（引雷与忠诚可共存，与激流互斥）
- 重锤：破甲 ↔ 致密（damage 组互斥）
- 护甲：保护 ↔ 火焰 ↔ 爆炸 ↔ 弹射物（四选一）

## 5. 是否可达
全部 23 件 Profile 的必需附魔均 `tableReachable = true`，无 UNREACHABLE。
荆棘 III 为「铁砧可达」（II+II→III），目标为极品档，属合法可达。

## 6. Candidate 数量
75/75（每件装备独立 30 级候选池文件）。

## 7. TargetMatcher 结果
新 Profile 目标均能通过严格等级匹配（效率 IV≠V、荆棘 II≠III 精确比对）。

## 8. Planner 结果
现有 AnvilPlanner（贡献排序贪心）可直接复用；荆棘 III 需铁砧 II+II 合并，由 Planner 规划。

## 9. 编译结果
`gradlew clean build` → **BUILD SUCCESSFUL**

## 10. 尚未进行
真机测试（附魔台实际结果、CandidateMatcher/TargetMatcher/Planner 判断、铁砧、成品箱）均未执行。
低端材质（木/石/铜/铁/金/皮革/锁链）Profile 按「不凑数」原则未生成，GUI 显示「暂无极品方案」。
