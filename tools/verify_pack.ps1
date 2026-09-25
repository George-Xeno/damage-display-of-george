# 把 Damage display of George 装进整合包，并检查日志里本模组相关的行。
#
# 用法：
#   .\tools\verify_pack.ps1                      # 用默认整合包「重度手搓症状」
#   .\tools\verify_pack.ps1 -PackName "别的整合包"
#
# 只做两件事：① 复制 jar；② 读日志过滤本模组的行。
# 不会自动启动游戏（游戏正开着时复制过去的 jar 也不会被加载）。

param(
    [string]$PackRoot = 'D:\PCL2\.minecraft\versions',
    [string]$PackName = '重度手搓症状',
    [string]$JarPath  = ''
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $JarPath) {
    $JarPath = Join-Path $repoRoot 'build\libs\damage-display-of-george-1.0.0.jar'
}

$packDir = Join-Path $PackRoot $PackName
$modsDir = Join-Path $packDir 'mods'
$logPath = Join-Path $packDir 'logs\latest.log'

# ── 前置检查 ──────────────────────────────────────────────
if (-not (Test-Path $JarPath)) {
    Write-Host "找不到 jar：$JarPath" -ForegroundColor Red
    Write-Host "先跑：.\gradlew build" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path $modsDir)) {
    Write-Host "找不到 mods 目录：$modsDir" -ForegroundColor Red
    exit 1
}

# ── 记录日志基线（判断游戏有没有重启过）────────────────────
$beforeWrite = if (Test-Path $logPath) { (Get-Item $logPath).LastWriteTime } else { $null }
$beforeCount = (Get-ChildItem $modsDir -Filter *.jar).Count

# ── 复制 jar；先确认没被游戏占用 ───────────────────────────
$target = Join-Path $modsDir (Split-Path $JarPath -Leaf)
if (Test-Path $target) {
    try {
        $fs = [IO.File]::Open($target, 'Open', 'ReadWrite', 'None')
        $fs.Close()
    } catch {
        Write-Host "游戏正在占用 $target —— 请先关闭游戏再装。" -ForegroundColor Red
        exit 2
    }
}

Copy-Item $JarPath $target -Force
$afterCount = (Get-ChildItem $modsDir -Filter *.jar).Count

Write-Host ""
Write-Host "已安装：$target" -ForegroundColor Green
Write-Host ("sha256  " + (Get-FileHash $target -Algorithm SHA256).Hash)
Write-Host "mods 数量：$beforeCount -> $afterCount"
Write-Host ""

# ── 读日志 ────────────────────────────────────────────────
if (-not (Test-Path $logPath)) {
    Write-Host "还没有日志（游戏没启动过）。启动一次游戏后再跑本脚本。" -ForegroundColor Yellow
    exit 0
}

$nowWrite = (Get-Item $logPath).LastWriteTime
if ($beforeWrite -and $nowWrite -le $beforeWrite) {
    Write-Host "日志没有更新（最后写入 $nowWrite）。" -ForegroundColor Yellow
    Write-Host "说明游戏还没重启过 —— 重启游戏后再跑一次本脚本。" -ForegroundColor Yellow
    exit 0
}

Write-Host "=== 本模组相关日志 ===" -ForegroundColor Cyan
Get-Content $logPath |
    Select-String -Pattern 'damage_display_of_george', 'Damage display of George' |
    Select-Object -First 40 |
    ForEach-Object { $_.Line }

Write-Host ""
Write-Host "=== 错误检查（本模组不该出现在这些行里）===" -ForegroundColor Cyan
$bad = Get-Content $logPath |
    Select-String -Pattern 'damage_display_of_george' |
    Select-String -Pattern 'ERROR|Exception|Failed'
if ($bad) {
    Write-Host "发现本模组相关错误：" -ForegroundColor Red
    $bad | ForEach-Object { $_.Line }
} else {
    Write-Host "没有本模组相关的 ERROR / Exception [OK]" -ForegroundColor Green
}

Write-Host ""
Write-Host "接下来：进游戏挨一下伤害（摔一下 / 被僵尸打），看聊天栏有没有 [伤害] 开头的行。" -ForegroundColor Cyan
