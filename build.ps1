# 一键构建 Spotlight（PowerShell）
# 用法：
#   .\build.ps1            # 编译 debug APK
#   .\build.ps1 -Test      # 先跑单元测试再编译
#   .\build.ps1 -Install   # 编译后用 adb 安装到已连接设备
#
# 需要 JDK 17+、Android SDK（platform 35、build-tools 35.0.0），
# 以及 Gradle 8.9（脚本会依次查找 PATH、项目内 wrapper、../tools/gradle-8.9）。
param(
    [switch]$Test,
    [switch]$Install,
    [string]$JavaHome = ''
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

function Resolve-JavaHome {
    if ($JavaHome) { return $JavaHome }
    foreach ($candidate in @(
            'C:\Program Files\Zulu\zulu-21',
            'C:\Program Files\Android\Android Studio\jbr',
            'C:\Program Files\Eclipse Adoptium\jdk-21',
            'C:\Program Files\Java\jdk-21'
        )) {
        if (Test-Path (Join-Path $candidate 'bin\java.exe')) { return $candidate }
    }
    return ''
}

function Resolve-Gradle {
    # 优先用本地 tools 里的 Gradle（离线可用），其次 wrapper，最后 PATH
    foreach ($candidate in @(
            (Join-Path $root '..\tools\gradle-8.9\bin\gradle.bat'),
            'C:\Gradle\gradle-8.9\bin\gradle.bat'
        )) {
        if (Test-Path $candidate) { return (Resolve-Path $candidate).Path }
    }
    if (Test-Path (Join-Path $root 'gradlew.bat')) { return (Join-Path $root 'gradlew.bat') }
    $fromPath = Get-Command gradle -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    throw '找不到 Gradle：请安装 Gradle 8.9，或把 gradle-8.9 放到项目的上级 tools 目录。'
}

$java = Resolve-JavaHome
if ($java) { $env:JAVA_HOME = $java }
if (-not $env:GRADLE_USER_HOME) {
    $cache = Join-Path $root '..\gradle-home'
    if (Test-Path $cache) { $env:GRADLE_USER_HOME = (Resolve-Path $cache).Path }
}

$gradle = Resolve-Gradle
$tasks = @()
if ($Test) { $tasks += ':app:testDebugUnitTest' }
$tasks += ':app:assembleDebug'

Write-Host "==> gradle $($tasks -join ' ')" -ForegroundColor Cyan
& $gradle --no-daemon -p $root @tasks --console=plain
if ($LASTEXITCODE -ne 0) { throw "构建失败（exit $LASTEXITCODE）" }

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "未找到产物：$apk" }

$outDir = Split-Path $root -Parent
$out = Join-Path $outDir 'Spotlight-1.0-debug.apk'
Copy-Item $apk $out -Force
$size = [math]::Round((Get-Item $out).Length / 1MB, 2)
Write-Host "==> 产物：$out ($size MB)" -ForegroundColor Green

if ($Install) {
    Write-Host '==> adb install -r' -ForegroundColor Cyan
    & adb install -r $out
}
