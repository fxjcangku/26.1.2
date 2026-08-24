$ErrorActionPreference = 'Stop'

$jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\meteordevelopment\meteor-client\26.1.2-SNAPSHOT" -Recurse -Filter '*.jar' |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $jar) {
    throw 'Meteor Client dependency JAR was not found. Run a Gradle build first.'
}

$classes = & jar tf $jar |
    Where-Object { $_ -match '^meteordevelopment/meteorclient/(gui/screens|systems/modules)/.+\.class$' } |
    ForEach-Object { $_.Replace('/', '.') -replace '\.class$', '' }

$output = & javap -classpath $jar -p -c $classes 2>$null |
    Select-String '// String ' |
    ForEach-Object { $_.Line -replace '^.*// String ', '' }

$output |
    Where-Object {
        $_ -match '[A-Za-z]' -and
        $_ -notmatch '[/\\]' -and
        $_ -notmatch '^https?://' -and
        $_.Length -le 80
    } |
    Sort-Object -Unique |
    Set-Content -Encoding utf8 build/meteor-ui-text-candidates.txt

$translator = Get-Content src/main/java/com/example/addon/YiyiaddonTranslator.java -Raw
$json = Get-Content src/main/resources/assets/yalu/lang/zh_cn.json -Raw
$covered = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

[regex]::Matches($translator, 'case "([^"]+)"') | ForEach-Object { [void] $covered.Add($_.Groups[1].Value) }
[regex]::Matches($translator, 'normalized\.equals\("([^"]+)"\)') | ForEach-Object { [void] $covered.Add($_.Groups[1].Value) }
[regex]::Matches($json, '(?m)^\s*"([^"]+)"\s*:') | ForEach-Object { [void] $covered.Add($_.Groups[1].Value) }

Get-Content build/meteor-ui-text-candidates.txt |
    Where-Object { -not $covered.Contains($_) } |
    Set-Content -Encoding utf8 build/meteor-ui-text-untranslated.txt

Write-Output 'Generated build/meteor-ui-text-candidates.txt and build/meteor-ui-text-untranslated.txt'
