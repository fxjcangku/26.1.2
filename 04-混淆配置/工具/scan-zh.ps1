# 混淆中文残留扫描工具
# 用途：扫描混淆 jar 解压目录里 com/example/addon 下的所有 .class，
#       读取每个类的常量池 UTF8 条目，找出仍含中文字符串的类。
#       用于验证字符串加密是否彻底——正常应只剩枚举类（枚举 name 必须明文保留）。
# 用法：.\scan-zh.ps1 [解压根目录]
#       不传参默认扫描 d:\mcaddon\yiyiaddon1.3-unpacked

param(
    [string]$Root = "d:\mcaddon\yiyiaddon1.3-unpacked"
)

# 待扫描的类目录：只关心本 addon 的 com/example/addon 命名空间
$classRoot = Join-Path $Root "com\example\addon"

# 解析 class 常量池，返回所有 UTF8 字符串条目（不做完整解析，只读常量的 UTF8 值）
function Get-CpUtf8 {
    param([byte[]]$bytes)
    $result = New-Object System.Collections.Generic.List[string]
    $count = ($bytes[8] * 256) + $bytes[9]
    $pos = 10
    $i = 1
    while ($i -lt $count -and $pos -lt $bytes.Length) {
        $tag = $bytes[$pos]
        $pos++
        if ($tag -eq 1) {
            $len = ($bytes[$pos] * 256) + $bytes[$pos+1]
            $pos += 2
            $s = [System.Text.Encoding]::UTF8.GetString($bytes, $pos, $len)
            $result.Add($s)
            $pos += $len
        }
        elseif ($tag -eq 7 -or $tag -eq 8 -or $tag -eq 16 -or $tag -eq 19 -or $tag -eq 20) { $pos += 2 }
        elseif ($tag -eq 15) { $pos += 3 }
        elseif ($tag -eq 3 -or $tag -eq 4) { $pos += 4 }
        elseif ($tag -eq 5 -or $tag -eq 6) { $pos += 8; $i++ }
        elseif ($tag -eq 9 -or $tag -eq 10 -or $tag -eq 11 -or $tag -eq 12 -or $tag -eq 17 -or $tag -eq 18) { $pos += 4 }
        else { return $result }
        $i++
    }
    return $result
}

if (-not (Test-Path $classRoot)) {
    Write-Error "找不到目录：$classRoot"
    exit 1
}

Get-ChildItem $classRoot -Recurse -Filter *.class | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    $utf8s = Get-CpUtf8 $bytes
    $zh = @($utf8s | Where-Object { $_ -match '[\u4e00-\u9fff]' })
    if ($zh.Count -gt 0) {
        $rel = $_.FullName.Replace($Root + '\','')
        Write-Output ("===== " + $rel + " =====")
        foreach ($s in $zh) { Write-Output ("  " + $s) }
    }
}
