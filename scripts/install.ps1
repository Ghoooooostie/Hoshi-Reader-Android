#Requires -Version 5.1
<#
.SYNOPSIS
    构建 Hoshi Reader APK 并安装到连接的 Android 设备/模拟器。

.DESCRIPTION
    按仓库约定执行完整安装流程：
      1. 补齐 $env:HOME（app/build.gradle.kts 用 HOME/.cargo/bin/cargo 定位 cargo，
         新 PowerShell 会话常无 HOME，会导致 CMake 构建失败）
      2. ./gradlew.bat :app:assemble<Variant>
      3. 选定设备（自动唯一设备，或用 -Serial 指定）
      4. adb install -r
      5. force-stop 后冷启动（WebView 资产 hoshi-web/** 有改动时必须冷启动才生效）

.EXAMPLE
    .\scripts\install.ps1
    构建 debug 并安装到唯一在线设备，然后冷启动。

.EXAMPLE
    .\scripts\install.ps1 -Serial 381QYGEM226CZ -SkipBuild
    跳过构建，直接安装已存在的 debug APK。

.EXAMPLE
    .\scripts\install.ps1 -Variant Release -NoLaunch
    构建并安装 release，安装后不启动。
#>
[CmdletBinding()]
param(
    # 构建变体：Debug（包名 moe.antimony.hoshi.debug）或 Release（包名 moe.antimony.hoshi）
    [ValidateSet('Debug', 'Release')]
    [string] $Variant = 'Debug',

    # 目标设备序列号；省略时要求有且仅有一台在线设备
    [string] $Serial,

    # 跳过构建，直接安装现有 APK
    [switch] $SkipBuild,

    # 安装后不冷启动 App
    [switch] $NoLaunch,

    # 覆盖 $env:HOME；cargo 位于 <CargoHome>\.cargo\bin\cargo.exe
    [string] $CargoHome = 'D:\AndroidCache',

    # 可降级安装（用于 release 覆盖更高 versionCode 的调试包）
    [switch] $AllowDowngrade
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $repoRoot 'gradlew.bat'
$variantLower = $Variant.ToLowerInvariant()
$packageName = if ($Variant -eq 'Debug') { 'moe.antimony.hoshi.debug' } else { 'moe.antimony.hoshi' }

function Invoke-Step {
    param(
        [Parameter(Mandatory)][string] $Title,
        [Parameter(Mandatory)][scriptblock] $Action
    )
    Write-Host "==> $Title" -ForegroundColor Cyan
    & $Action
}

if (Test-Path Env:HOME) {
    Write-Host "HOME = $env:HOME" -ForegroundColor DarkGray
} elseif (Test-Path Env:USERPROFILE) {
    $env:HOME = $env:USERPROFILE
    Write-Host "HOME 未设置，回退到 USERPROFILE = $env:HOME" -ForegroundColor Yellow
} else {
    $env:HOME = $CargoHome
    Write-Host "HOME 未设置，使用 $CargoHome" -ForegroundColor Yellow
}

if (-not (Test-Path $gradlew)) {
    throw "未找到 $gradlew，脚本需放在仓库 scripts/ 目录下。"
}

function Get-OnlineDevices {
    $online = @()
    foreach ($line in (adb devices)) {
        if ($line -match '^(\S+)\s+device\s*$') { $online += $Matches[1] }
    }
    return $online
}

if (-not $SkipBuild) {
    Invoke-Step "构建 :app:assemble$Variant" {
        Push-Location $repoRoot
        try {
            & $gradlew ":app:assemble$Variant" '--no-daemon'
            if ($LASTEXITCODE -ne 0) { throw "gradlew 失败，退出码 $LASTEXITCODE" }
        } finally {
            Pop-Location
        }
    }
} else {
    Write-Host '==> 跳过构建（-SkipBuild）' -ForegroundColor Cyan
}

$apkDir = Join-Path $repoRoot "app/build/outputs/apk/$variantLower"
if (-not (Test-Path $apkDir)) {
    throw "未找到 APK 目录 $apkDir，先运行一次不带 -SkipBuild 的构建。"
}
$apk = Get-ChildItem -Path $apkDir -Filter '*.apk' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) {
    throw "$apkDir 下没有 .apk 文件。"
}
Write-Host "APK: $($apk.FullName)" -ForegroundColor DarkGray

$devices = @(Get-OnlineDevices)
if ($Serial) {
    if ($devices -notcontains $Serial) {
        throw "设备 $Serial 不在线。当前在线设备：$($devices -join ', ')"
    }
    $target = $Serial
} elseif ($devices.Count -eq 1) {
    $target = $devices[0]
} elseif ($devices.Count -eq 0) {
    throw '没有在线设备（adb devices）。连接设备或启动模拟器后重试。'
} else {
    throw "有多台在线设备（$($devices -join ', ')），请用 -Serial 指定。"
}
Write-Host "设备: $target" -ForegroundColor DarkGray

$adbArgs = @('-s', $target, 'install', '-r')
if ($AllowDowngrade) { $adbArgs += '-d' }
$adbArgs += $apk.FullName

Invoke-Step "安装到 $target" {
    $output = & adb $adbArgs 2>&1
    $output | ForEach-Object { Write-Host "    $_" }
    $text = ($output | Out-String)
    if ($LASTEXITCODE -ne 0 -or $text -notmatch 'Success') {
        if ($text -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
            throw '安装失败：签名不一致，先卸载设备上的同名包（adb uninstall ' + $packageName + '）后重试。'
        }
        if ($text -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
            throw '安装失败：versionCode 更低，加 -AllowDowngrade 重试。'
        }
        throw "安装失败：`n$text"
    }
}

if (-not $NoLaunch) {
    Invoke-Step "冷启动 $packageName" {
        & adb -s $target shell am force-stop $packageName
        # monkey 是无需知道 Activity 名即可触发 LAUNCHER intent 的可靠方式。
        # 用 cmd 包裹重定向：monkey 会把参数回显到 stderr，直接管道会被
        # $ErrorActionPreference='Stop' 当成 NativeCommandError 终止脚本。
        & cmd /c "adb -s $target shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 >nul 2>nul"
    }
}

Write-Host "`n完成：$Variant 已安装到 $target" -ForegroundColor Green
