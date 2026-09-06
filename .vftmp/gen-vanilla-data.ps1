﻿# 一次性数据生成脚本：依据 Minecraft 26.1.2 反编译源码与游戏数据包标签，
# 精确计算 30 级附魔台候选池 / 互斥对 / 宝藏清单，固化为静态 JSON。
# 对应游戏算法：
#   - EnchantmentMenu.slotsChanged：槽位2等级 = max(selected, 书架数*2) = 30（15 书架恒定）
#   - EnchantmentHelper.selectEnchantment：cost += 1 + rand(e/4+1) + rand(e/4+1)；
#     span=±15%；round 后得到最终成本；首抽后 while(nextInt(50) <= cost) 继续抽取，cost 每次减半
#   - getAvailableEnchantmentResults：取「最高满足 minCost(L) <= cost <= maxCost(L) 的等级」
#   - 附魔来源流 = EnchantmentTags.IN_ENCHANTING_TABLE（= #non_treasure）；
#     过滤条件 isPrimaryItem(item) || 书本
#   - Cost.calculate(level) = base + perLevel * (level - 1)
$ErrorActionPreference = 'Stop'
$root = 'd:\mcaddon\26.1.2\src\main\resources\enchantment\vanilla'
foreach ($d in @('meta','items','enchantments','conflicts','candidates\level30','profiles')) {
  New-Item -ItemType Directory -Force -Path "$root\$d" | Out-Null
}

# ---- 权威附魔成本表（提取自 Enchantments.java bootstrap：weight/maxLevel/minCost/maxCost/互斥组/是否宝藏）----
# 字段: id, 中文名, weight, max, minB, minP, maxB, maxP, treasure, group(可空)
$t = @(
  @('protection','保护',10,4,1,11,12,11,$false,'armor'),
  @('fire_protection','火焰保护',5,4,10,8,18,8,$false,'armor'),
  @('blast_protection','爆炸保护',2,4,5,8,13,8,$false,'armor'),
  @('projectile_protection','弹射物保护',5,4,3,6,9,6,$false,'armor'),
  @('feather_falling','摔落保护',5,4,5,6,11,6,$false,$null),
  @('respiration','水下呼吸',2,3,10,10,40,10,$false,$null),
  @('aqua_affinity','水下速掘',2,1,1,0,41,0,$false,$null),
  @('thorns','荆棘',1,3,10,20,60,20,$false,$null),
  @('depth_strider','深海探索者',2,3,10,10,25,10,$false,'boots'),
  @('frost_walker','冰霜行者',2,2,10,10,25,10,$true,'boots'),
  @('binding_curse','绑定诅咒',1,1,25,0,50,0,$true,$null),
  @('soul_speed','灵魂疾行',1,3,10,10,25,10,$true,$null),
  @('swift_sneak','迅捷潜行',1,3,25,25,75,25,$true,$null),
  @('sharpness','锋利',10,5,1,11,21,11,$false,'damage'),
  @('smite','亡灵杀手',5,5,5,8,25,8,$false,'damage'),
  @('bane_of_arthropods','节肢杀手',5,5,5,8,25,8,$false,'damage'),
  @('knockback','击退',5,2,5,20,55,20,$false,$null),
  @('fire_aspect','火焰附加',2,2,10,20,60,20,$false,$null),
  @('looting','抢夺',2,3,15,9,65,9,$false,$null),
  @('sweeping_edge','横扫之刃',2,3,5,9,20,9,$false,$null),
  @('efficiency','效率',10,5,1,10,51,10,$false,$null),
  @('silk_touch','精准采集',1,1,15,0,65,0,$false,'mining'),
  @('unbreaking','耐久',5,3,5,8,55,8,$false,$null),
  @('fortune','时运',2,3,15,9,65,9,$false,'mining'),
  @('power','力量',10,5,1,10,16,10,$false,$null),
  @('punch','冲击',2,2,12,20,37,20,$false,$null),
  @('flame','火矢',2,1,20,0,50,0,$false,$null),
  @('infinity','无限',1,1,20,0,50,0,$false,'bow'),
  @('luck_of_the_sea','海之眷顾',2,3,15,9,65,9,$false,$null),
  @('lure','饵钓',2,3,15,9,65,9,$false,$null),
  @('loyalty','忠诚',5,3,12,7,50,0,$false,'riptide'),
  @('impaling','穿刺',2,5,1,8,21,8,$false,'damage'),
  @('riptide','激流',2,3,17,7,50,0,$false,'riptide'),
  @('lunge','突进',5,3,5,8,25,8,$false,$null),
  @('channeling','引雷',1,1,25,0,50,0,$false,'riptide'),
  @('multishot','多重射击',2,1,20,0,50,0,$false,'crossbow'),
  @('quick_charge','快速装填',5,3,12,20,50,0,$false,$null),
  @('piercing','穿透',10,4,1,10,50,0,$false,'crossbow'),
  @('density','致密',5,5,5,8,25,8,$false,'damage'),
  @('breach','破甲',2,4,15,9,65,9,$false,'damage'),
  @('wind_burst','风爆',2,3,15,9,65,9,$true,$null),
  @('mending','经验修补',2,1,25,25,75,25,$true,'bow'),
  @('vanishing_curse','消失诅咒',1,1,25,0,50,0,$true,$null)
)
$enchMap = @{}
foreach ($e in $t) {
  if ($e.Count -ne 10) { throw "附魔表字段数错误: $($e[0]) count=$($e.Count)" }
  $enchMap[$e[0]] = [pscustomobject]@{
    id=$e[0]; name=$e[1]; weight=$e[2]; max=$e[3]
    minB=$e[4]; minP=$e[5]; maxB=$e[6]; maxP=$e[7]
    treasure=$e[8]; group=$e[9]
  }
}

# ---- 权威互斥组（提取自 data/minecraft/tags/enchantment/exclusive_set/*.json）----
$groups = [ordered]@{
  armor    = @('protection','blast_protection','fire_protection','projectile_protection')
  boots    = @('frost_walker','depth_strider')
  bow      = @('infinity','mending')
  crossbow = @('multishot','piercing')
  damage   = @('sharpness','smite','bane_of_arthropods','impaling','density','breach')
  mining   = @('fortune','silk_touch')
  riptide  = @('loyalty','channeling')
}
# 校验：组内成员 group 字段与组名一致（Enchantments.java 中每个成员的 exclusiveWith 指向同一 tag）
foreach ($g in $groups.GetEnumerator()) {
  foreach ($m in $g.Value) {
    if ($enchMap[$m].group -ne $g.Key) { throw "组与附魔表不一致: $m -> $($enchMap[$m].group) vs $($g.Key)" }
  }
}

# ---- 派生成对互斥列表（双向，用于 O(1) 查询与校验器）----
$pairs = New-Object System.Collections.Generic.List[object]
foreach ($g in $groups.GetEnumerator()) {
  for ($i = 0; $i -lt $g.Value.Count; $i++) {
    for ($j = $i + 1; $j -lt $g.Value.Count; $j++) {
      $pairs.Add([pscustomobject]@{ a = $g.Value[$i]; b = $g.Value[$j]; group = $g.Key })
    }
  }
}

# ---- 装备表（提取自 item/enchantable/* 标签 + EnchantmentDefinition 的 supported/primary 判定）----
# 26.1.2 关键事实：melee_weapon = 剑 + 长矛（不含斧）→ 斧的锋利/亡灵杀手等
# primary=melee_weapon 的附魔无法从附魔台获得；斧仅得工具类附魔。
# 字段: id, 中文名, 类别, 附魔能力值, 附魔台可得附魔表
$gears = @(
  @('minecraft:diamond_pickaxe','钻石镐','TOOL',10,@('efficiency','fortune','silk_touch','unbreaking')),
  @('minecraft:diamond_axe','钻石斧','TOOL',10,@('efficiency','fortune','silk_touch','unbreaking')),
  @('minecraft:diamond_shovel','钻石锹','TOOL',10,@('efficiency','fortune','silk_touch','unbreaking')),
  @('minecraft:diamond_hoe','钻石锄','TOOL',10,@('efficiency','fortune','silk_touch','unbreaking')),
  @('minecraft:diamond_sword','钻石剑','MELEE_WEAPON',10,@('sharpness','smite','bane_of_arthropods','knockback','fire_aspect','looting','sweeping_edge','unbreaking')),
  @('minecraft:bow','弓','RANGED_WEAPON',1,@('power','punch','flame','infinity','unbreaking')),
  @('minecraft:crossbow','弩','RANGED_WEAPON',1,@('multishot','quick_charge','piercing','unbreaking')),
  @('minecraft:diamond_helmet','钻石头盔','ARMOR_HEAD',10,@('protection','fire_protection','blast_protection','projectile_protection','respiration','aqua_affinity','unbreaking')),
  @('minecraft:diamond_chestplate','钻石胸甲','ARMOR_CHEST',10,@('protection','fire_protection','blast_protection','projectile_protection','thorns','unbreaking')),
  @('minecraft:diamond_leggings','钻石护腿','ARMOR_LEGS',10,@('protection','fire_protection','blast_protection','projectile_protection','unbreaking')),
  @('minecraft:diamond_boots','钻石靴子','ARMOR_FEET',10,@('protection','fire_protection','blast_protection','projectile_protection','feather_falling','depth_strider','unbreaking'))
)
# 校验：装备表里的附魔必须都是非宝藏（附魔台可得）
foreach ($gear in $gears) {
  foreach ($enchId in $gear[4]) {
    if ($enchMap[$enchId].treasure) { throw "装备 $($gear[0]) 候选含宝藏附魔 $enchId" }
  }
}

# ---- 30 级候选池计算（对应游戏 selectEnchantment）----
# 槽位2等级 = 30；modified = (30 + 1 + r1 + r2) * (1 + span)，r1/r2 ∈ [0, e/4]，span ∈ [-0.15, +0.15]
# Java Math.round = floor(x + 0.5)
function MinCost($e, $l) { return $e.minB + $e.minP * ($l - 1) }
function MaxCost($e, $l) { return $e.maxB + $e.maxP * ($l - 1) }

$genTime = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

foreach ($gear in $gears) {
  $gid = $gear[0]; $e = $gear[3]
  $baseMin = 31
  $baseMax = 31 + 2 * [math]::Floor($e / 4)
  $costMin = [math]::Floor($baseMin * 0.85 + 0.5)
  $costMax = [math]::Floor($baseMax * 1.15 + 0.5)

  $pools = New-Object System.Collections.Generic.List[object]
  $reach = [ordered]@{}
  foreach ($enchId in $gear[4]) {
    $en = $enchMap[$enchId]
    $levels = New-Object System.Collections.Generic.List[int]
    foreach ($c in $costMin..$costMax) {
      for ($l = $en.max; $l -ge 1; $l--) {
        if ($c -ge (MinCost $en $l) -and $c -le (MaxCost $en $l)) {
          $levels.Add($l)
          break
        }
      }
    }
    $reach[$enchId] = @($levels | Sort-Object -Unique)
  }
  foreach ($c in $costMin..$costMax) {
    $entry = New-Object System.Collections.Generic.List[object]
    foreach ($enchId in $gear[4]) {
      $en = $enchMap[$enchId]
      for ($l = $en.max; $l -ge 1; $l--) {
        if ($c -ge (MinCost $en $l) -and $c -le (MaxCost $en $l)) {
          $entry.Add([pscustomobject]@{ enchantment = $enchId; level = $l; weight = $en.weight })
          break
        }
      }
    }
    $pools.Add([pscustomobject]@{ cost = $c; entries = $entry.ToArray() })
  }
  # 理论最大抽取次数：首抽 + 每次继续后 cost 减半，直到 cost 降到无法继续
  $maxRolls = 1 + [math]::Floor([math]::Log($costMax, 2)) + 1

  $obj = [pscustomobject]@{
    minecraft_version = '26.1.2'
    item              = $gid
    name              = $gear[1]
    category          = $gear[2]
    enchantability    = $e
    slot_level        = 30
    modified_cost_range = @($costMin, $costMax)
    cost_pools        = $pools.ToArray()
    reachable_levels  = $reach
    roll_model        = [pscustomobject]@{
      first_cost        = 'modified 最终成本（30 + 1 + 两个 rand(e/4+1)，±15% 后 round）'
      subsequent_rolls  = 'while(nextInt(50) <= cost) 继续抽取，cost 每次减半'
      compatibility     = '每次续抽前按 areCompatible 过滤（互斥组过滤，同名去重）'
      max_possible_rolls = $maxRolls
    }
    source            = 'Minecraft 26.1.2 EnchantmentHelper.selectEnchantment + tags/enchantment(non_treasure) + tags/item/enchantable'
    generated_at      = $genTime
  }
  $json = $obj | ConvertTo-Json -Depth 8
  [System.IO.File]::WriteAllText("$root\candidates\level30\$($gid.Replace('minecraft:',''))-level30.json", $json, (New-Object System.Text.UTF8Encoding($false)))
}

# ---- 互斥文件 ----
$grpObj = New-Object System.Collections.Generic.List[object]
foreach ($g in $groups.GetEnumerator()) {
  $grpObj.Add([pscustomobject]@{ id = $g.Key; members = @($g.Value) })
}
$conflicts = [pscustomobject]@{
  minecraft_version = '26.1.2'
  exclusive_groups  = $grpObj.ToArray()
  conflict_pairs    = $pairs.ToArray()
  source            = 'data/minecraft/tags/enchantment/exclusive_set/*.json'
  generated_at      = $genTime
}
[System.IO.File]::WriteAllText("$root\conflicts\conflicts.json", ($conflicts | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))

# ---- 附魔定义文件 ----
$encList = New-Object System.Collections.Generic.List[object]
foreach ($k in ($enchMap.Keys | Sort-Object)) {
  $en = $enchMap[$k]
  $encList.Add([pscustomobject]@{
    id                  = "minecraft:$($en.id)"
    name                = $en.name
    max_level           = $en.max
    weight              = $en.weight
    min_cost            = [pscustomobject]@{ base = $en.minB; per_level = $en.minP }
    max_cost            = [pscustomobject]@{ base = $en.maxB; per_level = $en.maxP }
    treasure            = $en.treasure
    obtainable_from_enchanting_table = -not $en.treasure
    exclusive_group     = $en.group
  })
}
$enchFile = [pscustomobject]@{
  minecraft_version = '26.1.2'
  source            = 'Minecraft 26.1.2 Enchantments.java + tags/enchantment/treasure.json'
  generated_at      = $genTime
  enchantments      = $encList.ToArray()
}
[System.IO.File]::WriteAllText("$root\enchantments\enchantments.json", ($enchFile | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))

# ---- 装备定义文件 ----
$gearList = New-Object System.Collections.Generic.List[object]
foreach ($gear in $gears) {
  $enchFull = New-Object System.Collections.Generic.List[string]
  foreach ($enchId in $gear[4]) { $enchFull.Add("minecraft:$enchId") }
  $gearList.Add([pscustomobject]@{
    id               = $gear[0]
    name             = $gear[1]
    category         = $gear[2]
    enchantability   = $gear[3]
    table_enchantments = $enchFull.ToArray()
  })
}
$gearFile = [pscustomobject]@{
  minecraft_version = '26.1.2'
  source            = 'Minecraft 26.1.2 ToolMaterial/ArmorMaterials/Items(ENCHANTABLE) + tags/item/enchantable + Enchantment.isPrimaryItem'
  generated_at      = $genTime
  items             = $gearList.ToArray()
}
[System.IO.File]::WriteAllText("$root\items\gears.json", ($gearFile | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))

# ---- 版本元信息文件 ----
$meta = [pscustomobject]@{
  minecraft_version = '26.1.2'
  data_version      = 1
  source            = 'Minecraft Wiki + Minecraft 26.1.2 反编译源码 + 游戏数据包标签'
  generated_at      = $genTime
  note              = '运行时只读取本目录静态数据，禁止联网查询 Wiki'
}
[System.IO.File]::WriteAllText("$root\meta\rules.json", ($meta | ConvertTo-Json -Depth 4), (New-Object System.Text.UTF8Encoding($false)))

Write-Output "OK: candidates=$($gears.Count) files, conflicts pairs=$($pairs.Count), enchantments=$($enchMap.Count), gears=$($gears.Count), cost window e10=[$costMin,$costMax]"