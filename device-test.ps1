# 连上手机后的自检脚本（需要 adb 能看到设备）
# 用法：
#   .\device-test.ps1              # 安装 + 启动 + 抓日志/截图
#   .\device-test.ps1 -Swipe       # 额外模拟一次桌面下滑（input swipe）
param(
    [switch]$Swipe,
    [string]$Apk = (Join-Path (Split-Path $PSScriptRoot -Parent) 'Spotlight-1.0-debug.apk')
)

$ErrorActionPreference = 'Stop'

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path $sdkAdb) { $adb = $sdkAdb }
}
if (-not $adb) { throw '找不到 adb：请安装 Android SDK platform-tools 并加入 PATH。' }

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    return & $adb @Args 2>&1
}

$devices = Invoke-Adb devices
Write-Host "== adb devices ==" -ForegroundColor Cyan
$devices | ForEach-Object { Write-Host $_ }

if (-not ($devices | Where-Object { $_ -match '\tdevice$' })) {
    throw '没有处于 device 状态的设备：请插 USB 并允许 USB 调试，或先 adb connect <手机IP>:<端口>。'
}

Write-Host "`n== 安装 APK ==" -ForegroundColor Cyan
Invoke-Adb install -r $Apk | ForEach-Object { Write-Host $_ }

Write-Host "`n== 清理日志并启动 ==" -ForegroundColor Cyan
Invoke-Adb logcat -c | Out-Null
Invoke-Adb shell am start -n com.spotlight.app/.ui.SpotlightActivity | ForEach-Object { Write-Host $_ }
Start-Sleep -Seconds 2

if ($Swipe) {
    Write-Host "`n== 模拟屏幕中部下滑 ==" -ForegroundColor Cyan
    Invoke-Adb shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Milliseconds 800
    Invoke-Adb shell input swipe 540 900 540 1700 250 | Out-Null
    Start-Sleep -Seconds 1
}

$shot = Join-Path $PSScriptRoot 'device-screenshot.png'
Write-Host "`n== 截图 ==" -ForegroundColor Cyan
Invoke-Adb exec-out screencap -p > $shot
Write-Host "截图：$shot"

Write-Host "`n== Spotlight / Xposed 相关日志 ==" -ForegroundColor Cyan
Invoke-Adb logcat -d -v time |
    Select-String -Pattern 'Spotlight|Xposed|LSPosed|com.spotlight.app|FATAL EXCEPTION|AndroidRuntime' |
    Select-Object -Last 80 |
    ForEach-Object { $_.Line }

Write-Host "`n== 当前前台 Activity ==" -ForegroundColor Cyan
Invoke-Adb shell dumpsys activity activities |
    Select-String -Pattern 'mResumedActivity|topResumedActivity' |
    ForEach-Object { $_.Line.Trim() }
