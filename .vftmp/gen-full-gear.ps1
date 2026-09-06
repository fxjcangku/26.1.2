﻿# 一次性生成脚本：依据 Minecraft 26.1.2 官方 Item Registry / ToolMaterial / ArmorMaterials
# 生成「原版装备极品附魔」完整装备全集（75 件）的 gears.json 与 30 级候选池。
# 严格以官方数据为准：铜工具/铜护甲/矛/重锤等 26.1.2 真实 Item 才加入，绝不虚构。
$ErrorActionPreference = 'Stop'
$root = 'd:\mcaddon\26.1.2\src\main\resources\enchantment\vanilla'
New-Item -ItemType Directory -Force -Path "$root\items" | Out-Null
New-Item -ItemType Directory -Force -Path "$root\candidates\level30" | Out-Null

# ---- 附魔成本表（与 enchantments.json 一致：id, 中文名, weight, max, minB, minP, maxB, maxP, treasure, group）----
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
  $enchMap[$e[0]] = [pscustomobject]@{
    id=$e[0]; name=$e[1]; weight=$e[2]; max=$e[3]
    minB=$e[4]; minP=$e[5]; maxB=$e[6]; maxP=$e[7]
    treasure=$e[8]; group=$e[9]
  }
}

# ---- 装备类型附魔规则（附魔台可得，已按 isPrimaryItem 过滤，非宝藏）----
# 字段: 类型英文, 类型中文, category, 附魔表
$toolEnch  = @('efficiency','fortune','silk_touch','unbreaking')
$swordEnch = @('sharpness','smite','bane_of_arthropods','knockback','fire_aspect','looting','sweeping_edge','unbreaking')
$spearEnch = @('sharpness','smite','bane_of_arthropods','knockback','fire_aspect','looting','lunge','unbreaking')
$bowEnch   = @('power','punch','flame','infinity','unbreaking')
$crossbowEnch = @('multishot','quick_charge','piercing','unbreaking')
$tridentEnch = @('loyalty','impaling','riptide','channeling','unbreaking')
$maceEnch  = @('breach','density','unbreaking')
$headEnch  = @('protection','fire_protection','blast_protection','projectile_protection','respiration','aqua_affinity','unbreaking')
$chestEnch = @('protection','fire_protection','blast_protection','projectile_protection','thorns','unbreaking')
$legsEnch  = @('protection','fire_protection','blast_protection','projectile_protection','unbreaking')
$feetEnch  = @('protection','fire_protection','blast_protection','projectile_protection','feather_falling','depth_strider','unbreaking')

# ---- 材质（material, 中文, enchantability）----
# 工具/剑/矛材质（ToolMaterial.enchantmentValue）
$toolMats = @(
  @('wooden','木',15), @('stone','石',5), @('copper','铜',13),
  @('iron','铁',14), @('golden','金',22), @('diamond','钻石',10), @('netherite','下界合金',15)
)
# 护甲材质（ArmorMaterial.enchantmentValue）
$armorMats = @(
  @('leather','皮革',15), @('copper','铜',8), @('chainmail','锁链',12),
  @('iron','铁',9), @('golden','金',25), @('diamond','钻石',10), @('netherite','下界合金',15)
)

# ---- 组装装备全集（id, 中文名, category, material, enchantability, 附魔表）----
$gears = New-Object System.Collections.Generic.List[object]
function AddGear($id, $name, $cat, $mat, $enc, $ench) {
  $script:gears.Add([pscustomobject]@{ id=$id; name=$name; category=$cat; material=$mat; enchantability=$enc; ench=$ench })
}

# 工具：镐/斧/锹/锄 × 7 材质
$toolTypes = @(
  @('pickaxe','镐','TOOL_PICKAXE'), @('axe','斧','TOOL_AXE'),
  @('shovel','锹','TOOL_SHOVEL'), @('hoe','锄','TOOL_HOE')
)
foreach ($tt in $toolTypes) {
  foreach ($m in $toolMats) {
    AddGear "minecraft:$($m[0])_$($tt[0])" "$($m[1])$($tt[1])" $tt[2] $m[0] $m[2] $toolEnch
  }
}
# 剑 × 7 材质
foreach ($m in $toolMats) {
  AddGear "minecraft:$($m[0])_sword" "$($m[1])剑" 'WEAPON_SWORD' $m[0] $m[2] $swordEnch
}
# 矛 × 7 材质（26.1.2 新武器）
foreach ($m in $toolMats) {
  AddGear "minecraft:$($m[0])_spear" "$($m[1])矛" 'WEAPON_SPEAR' $m[0] $m[2] $spearEnch
}
# 弓 / 弩 / 三叉戟 / 重锤
AddGear 'minecraft:bow'       '弓'     'WEAPON_BOW'       'none' 1  $bowEnch
AddGear 'minecraft:crossbow'  '弩'     'WEAPON_CROSSBOW'  'none' 1  $crossbowEnch
AddGear 'minecraft:trident'   '三叉戟' 'WEAPON_TRIDENT'   'none' 1  $tridentEnch
AddGear 'minecraft:mace'      '重锤'   'WEAPON_MACE'      'none' 15 $maceEnch
# 护甲：头盔/胸甲/护腿/靴子 × 7 材质
$armorTypes = @(
  @('helmet','头盔','ARMOR_HELMET',$headEnch), @('chestplate','胸甲','ARMOR_CHESTPLATE',$chestEnch),
  @('leggings','护腿','ARMOR_LEGGINGS',$legsEnch), @('boots','靴子','ARMOR_BOOTS',$feetEnch)
)
foreach ($at in $armorTypes) {
  foreach ($m in $armorMats) {
    AddGear "minecraft:$($m[0])_$($at[0])" "$($m[1])$($at[1])" $at[2] $m[0] $m[2] $at[3]
  }
}
# 海龟壳（特殊头盔）
AddGear 'minecraft:turtle_helmet' '海龟壳' 'ARMOR_HELMET' 'turtle' 9 $headEnch

# ---- 30 级候选池计算（对应 selectEnchantment）----
function MinCost($e, $l) { return $e.minB + $e.minP * ($l - 1) }
function MaxCost($e, $l) { return $e.maxB + $e.maxP * ($l - 1) }
$genTime = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

foreach ($gear in $gears) {
  $e = $gear.enchantability
  $baseMin = 31
  $baseMax = 31 + 2 * [math]::Floor($e / 4)
  $costMin = [math]::Floor($baseMin * 0.85 + 0.5)
  $costMax = [math]::Floor($baseMax * 1.15 + 0.5)

  $pools = New-Object System.Collections.Generic.List[object]
  $reach = [ordered]@{}
  foreach ($enchId in $gear.ench) {
    $en = $enchMap[$enchId]
    $levels = New-Object System.Collections.Generic.List[int]
    foreach ($c in $costMin..$costMax) {
      for ($l = $en.max; $l -ge 1; $l--) {
        if ($c -ge (MinCost $en $l) -and $c -le (MaxCost $en $l)) {
          $levels.Add($l); break
        }
      }
    }
    $reach[$enchId] = @($levels | Sort-Object -Unique)
  }
  foreach ($c in $costMin..$costMax) {
    $entry = New-Object System.Collections.Generic.List[object]
    foreach ($enchId in $gear.ench) {
      $en = $enchMap[$enchId]
      for ($l = $en.max; $l -ge 1; $l--) {
        if ($c -ge (MinCost $en $l) -and $c -le (MaxCost $en $l)) {
          $entry.Add([pscustomobject]@{ enchantment = $enchId; level = $l; weight = $en.weight }); break
        }
      }
    }
    $pools.Add([pscustomobject]@{ cost = $c; entries = $entry.ToArray() })
  }
  $maxRolls = 1 + [math]::Floor([math]::Log($costMax, 2)) + 1

  $obj = [pscustomobject]@{
    minecraft_version = '26.1.2'
    item = $gear.id
    name = $gear.name
    category = $gear.category
    material = $gear.material
    enchantability = $e
    slot_level = 30
    modified_cost_range = @($costMin, $costMax)
    cost_pools = $pools.ToArray()
    reachable_levels = $reach
    roll_model = [pscustomobject]@{
      first_cost = 'modified 最终成本（30 + 1 + 两个 rand(e/4+1)，±15% 后 round）'
      subsequent_rolls = 'while(nextInt(50) <= cost) 继续抽取，cost 每次减半'
      compatibility = '每次续抽前按 areCompatible 过滤（互斥组过滤，同名去重）'
      max_possible_rolls = $maxRolls
    }
    source = 'Minecraft 26.1.2 EnchantmentHelper.selectEnchantment + tags/enchantment(non_treasure) + tags/item/enchantable'
    generated_at = $genTime
  }
  $json = $obj | ConvertTo-Json -Depth 8
  [System.IO.File]::WriteAllText("$root\candidates\level30\$($gear.id.Replace('minecraft:',''))-level30.json", $json, (New-Object System.Text.UTF8Encoding($false)))
}

# ---- 装备定义文件（含 category + material，供 GUI 三级分类）----
$gearList = New-Object System.Collections.Generic.List[object]
foreach ($gear in $gears) {
  $enchFull = New-Object System.Collections.Generic.List[string]
  foreach ($enchId in $gear.ench) { $enchFull.Add("minecraft:$enchId") }
  $gearList.Add([pscustomobject]@{
    id = $gear.id
    name = $gear.name
    category = $gear.category
    material = $gear.material
    enchantability = $gear.enchantability
    table_enchantments = $enchFull.ToArray()
  })
}
$gearFile = [pscustomobject]@{
  minecraft_version = '26.1.2'
  source = 'Minecraft 26.1.2 Items.java(ToolMaterial/ArmorMaterials) + tags/item/enchantable + Enchantment.isPrimaryItem'
  generated_at = $genTime
  items = $gearList.ToArray()
}
[System.IO.File]::WriteAllText("$root\items\gears.json", ($gearFile | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))

Write-Output "OK: gears=$($gears.Count), candidates=$($gears.Count) files"
