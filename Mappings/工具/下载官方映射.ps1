# 下载 Minecraft 26.1.2 官方映射原文件
#
# 用法（在项目根目录执行）：
#   powershell -ExecutionPolicy Bypass -File Mappings\工具\下载官方映射.ps1
#
# 为什么需要这个脚本：
#   Mojang EULA 禁止「完整且未修改地再分发」官方映射，
#   所以 Mappings/官方映射原文件/ 不进 Git，clone 之后跑一次本脚本重建即可。
#
# 之后依次运行三个生成脚本：
#   node Mappings\工具\提取映射索引.js
#   node Mappings\工具\生成分类速查表.js
#   node Mappings\工具\验证易错API.js

$ErrorActionPreference = 'Stop'

# 26.1.2 的内部版本号是 1.21.11，Loom 缓存目录也按这个命名
$内部版本 = '1.21.11'
$缓存信息 = Join-Path $env:USERPROFILE ".gradle\caches\fabric-loom\$内部版本\mojang_minecraft_info.json"
$输出目录 = Join-Path $PSScriptRoot '..\官方映射原文件'

New-Item -ItemType Directory -Force -Path $输出目录 | Out-Null

if (Test-Path $缓存信息) {
    Write-Host "从 Loom 缓存读取下载地址：$缓存信息"
    $信息 = Get-Content $缓存信息 -Raw | ConvertFrom-Json
} else {
    Write-Host "Loom 缓存不存在，改从 Mojang 版本清单查询 $内部版本"
    $清单 = Invoke-RestMethod -Uri 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'
    $条目 = $清单.versions | Where-Object { $_.id -eq $内部版本 }
    if (-not $条目) { throw "版本清单里找不到 $内部版本" }
    $信息 = Invoke-RestMethod -Uri $条目.url
}

foreach ($项 in @(
    @{ 键 = 'client_mappings'; 文件 = "client-$内部版本.txt" },
    @{ 键 = 'server_mappings'; 文件 = "server-$内部版本.txt" }
)) {
    $地址 = $信息.downloads.($项.键).url
    $目标 = Join-Path $输出目录 $项.文件
    Write-Host "下载 $($项.文件) ..."
    Invoke-WebRequest -Uri $地址 -OutFile $目标
    $大小 = [math]::Round((Get-Item $目标).Length / 1MB, 2)
    Write-Host "  完成，$大小 MB"
}

Write-Host ''
Write-Host '映射原文件已就绪。接下来运行：'
Write-Host '  node Mappings\工具\提取映射索引.js'
Write-Host '  node Mappings\工具\生成分类速查表.js'
Write-Host '  node Mappings\工具\验证易错API.js'
