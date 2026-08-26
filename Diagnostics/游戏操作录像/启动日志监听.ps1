# 游戏操作日志监听服务
# 用途：接收游戏内埋点发来的操作日志，保存为 NDJSON 格式
# 使用：修改下方的 $bugName，然后运行本脚本

$bugName = "服务器检测-两个bug"  # 修改这里
$date = Get-Date -Format "yyyy-MM-dd"
$logDir = $PSScriptRoot
$logFile = Join-Path $logDir "$date-$bugName.ndjson"
$port = 7777

Write-Host "日志监听服务已启动" -ForegroundColor Green
Write-Host "监听端口: http://127.0.0.1:$port" -ForegroundColor Cyan
Write-Host "日志文件: $logFile" -ForegroundColor Cyan
Write-Host "按 Ctrl+C 停止" -ForegroundColor Yellow
Write-Host ""

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$port/")
$listener.Start()

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response
        
        if ($request.HttpMethod -eq "POST") {
            $reader = New-Object System.IO.StreamReader($request.InputStream, $request.ContentEncoding)
            $body = $reader.ReadToEnd()
            $reader.Close()
            
            Add-Content -Path $logFile -Value $body -Encoding UTF8
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 收到日志" -ForegroundColor Gray
            
            $response.StatusCode = 200
        }
        elseif ($request.HttpMethod -eq "GET" -and $request.Url.AbsolutePath -eq "/health") {
            $response.StatusCode = 200
        }
        else {
            $response.StatusCode = 404
        }
        
        $response.Close()
    }
}
finally {
    $listener.Stop()
    Write-Host "监听服务已停止" -ForegroundColor Red
}
