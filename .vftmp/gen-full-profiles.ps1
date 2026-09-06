﻿# 生成完整 75 件装备的极品 Profile（gear-enchants.json）。
# 附魔规则按「装备类型」决定（与材质无关），故低端材质复用同类型高档材质的极品方案。
# 唯一差异是 enchantability（影响附魔台可达等级），但不改变极品目标等级。
$ErrorActionPreference = 'Stop'

# ---- 附魔中文名 ----
$enchName = @{
  'sharpness'='锋利'; 'smite'='亡灵杀手'; 'bane_of_arthropods'='节肢杀手';
  'knockback'='击退'; 'fire_aspect'='火焰附加'; 'looting'='抢夺'; 'sweeping_edge'='横扫之刃';
  'unbreaking'='耐久'; 'efficiency'='效率'; 'fortune'='时运'; 'silk_touch'='精准采集';
  'power'='力量'; 'punch'='冲击'; 'flame'='火矢'; 'infinity'='无限';
  'multishot'='多重射击'; 'quick_charge'='快速装填'; 'piercing'='穿透';
  'protection'='保护'; 'fire_protection'='火焰保护'; 'blast_protection'='爆炸保护'; 'projectile_protection'='弹射物保护';
  'respiration'='水下呼吸'; 'aqua_affinity'='水下速掘'; 'thorns'='荆棘';
  'feather_falling'='摔落缓冲'; 'depth_strider'='深海探索者';
  'lunge'='突进'; 'loyalty'='忠诚'; 'impaling'='穿刺'; 'riptide'='激流'; 'channeling'='引雷';
  'breach'='破甲'; 'density'='致密'
}

# ---- 目标条目构造 ----
function T($id, $level, $excludable) {
  return [pscustomobject]@{ id = "minecraft:$id"; name = $enchName[$id]; level = $level; excludable = $excludable }
}

# ---- Profile 模板（每个装备类型 → profiles 数组，含 id/name/default/exclusiveWith/targets）----
function ToolProfiles() {
  return @(
    [pscustomobject]@{ id='fortune'; name='时运'; default=$true; exclusiveWith=@('silk_touch'); targets=@((T 'fortune' 3 $false),(T 'efficiency' 5 $true),(T 'unbreaking' 3 $true)) },
    [pscustomobject]@{ id='silk_touch'; name='精准采集'; default=$false; exclusiveWith=@('fortune'); targets=@((T 'silk_touch' 1 $false),(T 'efficiency' 5 $true),(T 'unbreaking' 3 $true)) }
  )
}

function SwordProfiles() {
  $base = @('unbreaking','sweeping_edge','looting','fire_aspect','knockback')
  return @(
    [pscustomobject]@{ id='sharpness'; name='锋利'; default=$true; exclusiveWith=@('smite','bane_of_arthropods'); targets=@((T 'sharpness' 4 $false),(T 'unbreaking' 3 $true),(T 'sweeping_edge' 3 $true),(T 'looting' 3 $true),(T 'fire_aspect' 2 $true),(T 'knockback' 2 $true)) },
    [pscustomobject]@{ id='smite'; name='亡灵杀手'; default=$false; exclusiveWith=@('sharpness','bane_of_arthropods'); targets=@((T 'smite' 4 $false),(T 'unbreaking' 3 $true),(T 'sweeping_edge' 3 $true),(T 'looting' 3 $true),(T 'fire_aspect' 2 $true),(T 'knockback' 2 $true)) },
    [pscustomobject]@{ id='bane_of_arthropods'; name='节肢杀手'; default=$false; exclusiveWith=@('sharpness','smite'); targets=@((T 'bane_of_arthropods' 4 $false),(T 'unbreaking' 3 $true),(T 'sweeping_edge' 3 $true),(T 'looting' 3 $true),(T 'fire_aspect' 2 $true),(T 'knockback' 2 $true)) }
  )
}

function SpearProfiles() {
  return @(
    [pscustomobject]@{ id='sharpness'; name='锋利'; default=$true; exclusiveWith=@('smite','bane_of_arthropods'); targets=@((T 'sharpness' 4 $false),(T 'lunge' 3 $true),(T 'unbreaking' 3 $true),(T 'looting' 3 $true),(T 'fire_aspect' 2 $true),(T 'knockback' 2 $true)) },
    [pscustomobject]@{ id='smite'; name='亡灵杀手'; default=$false; exclusiveWith=@('sharpness','bane_of_arthropods'); targets=@((T 'smite' 4 $false),(T 'lunge' 3 $true),(T 'unbreaking' 3 $true),(T 'looting' 3 $true),(T 'fire_aspect' 2 $true),(T 'knockback' 2 $true)) },
    [pscustomobject]@{ id='bane_of_arthropods'; name='节肢杀手'; default=$false; exclusiveWith=@('sharpness','smite'); targets=@((T 'bane_of_arthropods' 4 $false),(T 'lunge' 3 $true),(T 'unbreaking' 3 $true),(T 'looting' 3 $true),(T 'fire_aspect' 2 $true),(T 'knockback' 2 $true)) }
  )
}

function BowProfiles() {
  return @(
    [pscustomobject]@{ id='power'; name='力量'; default=$true; exclusiveWith=@(); targets=@((T 'power' 4 $false),(T 'unbreaking' 3 $true),(T 'punch' 2 $true),(T 'flame' 1 $true),(T 'infinity' 1 $true)) }
  )
}

function CrossbowProfiles() {
  return @(
    [pscustomobject]@{ id='multishot'; name='多重射击'; default=$true; exclusiveWith=@('piercing'); targets=@((T 'multishot' 1 $false),(T 'quick_charge' 3 $true),(T 'unbreaking' 3 $true)) },
    [pscustomobject]@{ id='piercing'; name='穿透'; default=$false; exclusiveWith=@('multishot'); targets=@((T 'piercing' 4 $false),(T 'quick_charge' 3 $true),(T 'unbreaking' 3 $true)) }
  )
}

function TridentProfiles() {
  return @(
    [pscustomobject]@{ id='loyalty'; name='忠诚'; default=$true; exclusiveWith=@('riptide'); targets=@((T 'loyalty' 3 $false),(T 'impaling' 5 $true),(T 'channeling' 1 $true),(T 'unbreaking' 3 $true)) },
    [pscustomobject]@{ id='riptide'; name='激流'; default=$false; exclusiveWith=@('loyalty'); targets=@((T 'riptide' 3 $false),(T 'impaling' 5 $true),(T 'unbreaking' 3 $true)) }
  )
}

function MaceProfiles() {
  return @(
    [pscustomobject]@{ id='breach'; name='破甲'; default=$true; exclusiveWith=@('density'); targets=@((T 'breach' 4 $false),(T 'unbreaking' 3 $true)) },
    [pscustomobject]@{ id='density'; name='致密'; default=$false; exclusiveWith=@('breach'); targets=@((T 'density' 5 $false),(T 'unbreaking' 3 $true)) }
  )
}

function HelmetProfiles() {
  $prot = @('protection','fire_protection','blast_protection','projectile_protection')
  $res = @()
  foreach ($p in $prot) {
    $others = @($prot | Where-Object { $_ -ne $p })
    $res += [pscustomobject]@{ id=$p; name=$enchName[$p]; default=($p -eq 'protection'); exclusiveWith=$others; targets=@((T $p 4 $false),(T 'unbreaking' 3 $true),(T 'respiration' 3 $true),(T 'aqua_affinity' 1 $true)) }
  }
  return $res
}

function ChestplateProfiles() {
  $prot = @('protection','fire_protection','blast_protection','projectile_protection')
  $res = @()
  foreach ($p in $prot) {
    $others = @($prot | Where-Object { $_ -ne $p })
    $res += [pscustomobject]@{ id=$p; name=$enchName[$p]; default=($p -eq 'protection'); exclusiveWith=$others; targets=@((T $p 4 $false),(T 'unbreaking' 3 $true),(T 'thorns' 3 $true)) }
  }
  return $res
}

function LeggingsProfiles() {
  $prot = @('protection','fire_protection','blast_protection','projectile_protection')
  $res = @()
  foreach ($p in $prot) {
    $others = @($prot | Where-Object { $_ -ne $p })
    $res += [pscustomobject]@{ id=$p; name=$enchName[$p]; default=($p -eq 'protection'); exclusiveWith=$others; targets=@((T $p 4 $false),(T 'unbreaking' 3 $true)) }
  }
  return $res
}

function BootsProfiles() {
  $prot = @('protection','fire_protection','blast_protection','projectile_protection')
  $res = @()
  foreach ($p in $prot) {
    $others = @($prot | Where-Object { $_ -ne $p })
    $res += [pscustomobject]@{ id=$p; name=$enchName[$p]; default=($p -eq 'protection'); exclusiveWith=$others; targets=@((T $p 4 $false),(T 'unbreaking' 3 $true),(T 'feather_falling' 4 $true),(T 'depth_strider' 3 $true)) }
  }
  return $res
}

# ---- 材质（前缀, 中文）----
$toolMats = @(@('wooden','木'),@('stone','石'),@('copper','铜'),@('iron','铁'),@('golden','金'),@('diamond','钻石'),@('netherite','下界合金'))
$armorMats = @(@('leather','皮革'),@('chainmail','锁链'),@('copper','铜'),@('iron','铁'),@('golden','金'),@('diamond','钻石'),@('netherite','下界合金'))

# ---- 组装 75 件装备 ----
$gears = New-Object System.Collections.Generic.List[object]
function AddGear($id, $name, $cat, $profiles) {
  $script:gears.Add([pscustomobject]@{ id = $id; name = $name; category = $cat; profiles = $profiles })
}

foreach ($m in $toolMats) {
  AddGear "minecraft:$($m[0])_pickaxe" "$($m[1])镐" 'weapon' (ToolProfiles)
  AddGear "minecraft:$($m[0])_axe"     "$($m[1])斧" 'weapon' (ToolProfiles)
  AddGear "minecraft:$($m[0])_shovel"  "$($m[1])锹" 'weapon' (ToolProfiles)
  AddGear "minecraft:$($m[0])_hoe"     "$($m[1])锄" 'weapon' (ToolProfiles)
  AddGear "minecraft:$($m[0])_sword"   "$($m[1])剑" 'weapon' (SwordProfiles)
  AddGear "minecraft:$($m[0])_spear"   "$($m[1])矛" 'weapon' (SpearProfiles)
}
AddGear 'minecraft:bow'      '弓'     'weapon' (BowProfiles)
AddGear 'minecraft:crossbow' '弩'     'weapon' (CrossbowProfiles)
AddGear 'minecraft:trident'  '三叉戟' 'weapon' (TridentProfiles)
AddGear 'minecraft:mace'     '重锤'   'weapon' (MaceProfiles)
foreach ($m in $armorMats) {
  AddGear "minecraft:$($m[0])_helmet"     "$($m[1])头盔" 'armor' (HelmetProfiles)
  AddGear "minecraft:$($m[0])_chestplate" "$($m[1])胸甲" 'armor' (ChestplateProfiles)
  AddGear "minecraft:$($m[0])_leggings"   "$($m[1])护腿" 'armor' (LeggingsProfiles)
  AddGear "minecraft:$($m[0])_boots"      "$($m[1])靴子" 'armor' (BootsProfiles)
}
AddGear 'minecraft:turtle_helmet' '海龟壳' 'armor' (HelmetProfiles)

$root = [pscustomobject]@{ gears = $gears.ToArray() }
$json = $root | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText('d:\mcaddon\26.1.2\src\main\resources\assets\yiyiaddon\gear-enchants.json', $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "OK: gears = $($gears.Count)"
