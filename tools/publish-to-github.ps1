# publish-to-github.ps1
#
# 把 damage-display-of-george 发布到 GitHub（公开仓库）。
#
# 为什么需要这个脚本：
#   本机网络下 github.com 网页被墙，但 api.github.com 通、SSH(443) 通。
#   所以走 API 建仓库 + SSH 推送。
#
# 用法（在你自己的终端里运行）：
#   cd C:\Users\George\Documents\damage-display-of-george
#   .\tools\publish-to-github.ps1
#
# Token 需要什么权限：classic token 勾 "repo"；fine-grained token 选
#   "Administration: Read and write"（建仓库用）。
# 生成地址：https://github.com/settings/tokens

[CmdletBinding()]
param(
    [string]$RepoName = 'damage-display-of-george',
    [string]$Owner    = 'George-Xeno',
    [string]$Description = '在聊天栏显示你受到的伤害：伤害类型、来源、各项减免，以及最终实际掉了多少血。Minecraft 1.21.1 / NeoForge 21.1.x。',
    [switch]$Private
)

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'
$repoDir = Split-Path -Parent $PSScriptRoot
Set-Location $repoDir

Write-Host "=== Damage display of George -> GitHub ===" -ForegroundColor Cyan
Write-Host "仓库目录: $repoDir"

# ---- 1. 读 Token（隐藏输入，不留痕迹） ----
Write-Host ""
Write-Host "请粘贴 GitHub Personal Access Token（输入时不显示，直接回车确认）:" -ForegroundColor Yellow
$secure = Read-Host -AsSecureString
$token  = [System.Net.NetworkCredential]::new('', $secure).Password

if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host "X 没有输入 Token，已取消。" -ForegroundColor Red
    exit 1
}

$headers = @{
    Authorization          = "Bearer $token"
    Accept                 = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
    'User-Agent'           = 'publish-script'
}

# ---- 2. 校验 Token 是谁 ----
Write-Host ""
Write-Host "[1/4] 校验 Token ..." -ForegroundColor Green
try {
    $me = Invoke-RestMethod -Uri 'https://api.github.com/user' -Headers $headers -TimeoutSec 30
    Write-Host "  OK 已认证为: $($me.login)" -ForegroundColor Green
    if ($me.login -ne $Owner) {
        Write-Host "  注意: Token 属于 '$($me.login)'，不是 '$Owner'，将使用实际账号。" -ForegroundColor Yellow
        $Owner = $me.login
    }
} catch {
    Write-Host "  X Token 校验失败: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "    请确认 Token 有效且勾选了 repo / Administration 权限。" -ForegroundColor Yellow
    exit 1
}

# ---- 3. 建仓库（已存在则复用） ----
Write-Host ""
Write-Host "[2/4] 创建远程仓库 $Owner/$RepoName ..." -ForegroundColor Green
$body = @{
    name        = $RepoName
    description = $Description
    private     = [bool]$Private
    has_issues  = $true
    has_wiki    = $false
} | ConvertTo-Json

try {
    $repo = Invoke-RestMethod -Uri 'https://api.github.com/user/repos' -Method Post `
        -Headers $headers -Body $body -ContentType 'application/json' -TimeoutSec 60
    Write-Host "  OK 已创建: $($repo.html_url)" -ForegroundColor Green
} catch {
    $code = $null
    try { $code = $_.Exception.Response.StatusCode.value__ } catch {}
    if ($code -eq 422) {
        Write-Host "  仓库已存在，复用它。" -ForegroundColor Yellow
        $repo = Invoke-RestMethod -Uri "https://api.github.com/repos/$Owner/$RepoName" -Headers $headers -TimeoutSec 30
        Write-Host "  OK 复用: $($repo.html_url)" -ForegroundColor Green
    } else {
        Write-Host "  X 建仓库失败 (HTTP $code): $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

# ---- 4. 挂 SSH remote 并推送 ----
Write-Host ""
Write-Host "[3/4] 配置 remote (SSH) ..." -ForegroundColor Green
$sshUrl = "git@github.com:$Owner/$RepoName.git"
$existing = git remote 2>$null
if ($existing -contains 'origin') {
    git remote set-url origin $sshUrl
    Write-Host "  已更新 origin -> $sshUrl"
} else {
    git remote add origin $sshUrl
    Write-Host "  已添加 origin -> $sshUrl"
}

Write-Host ""
Write-Host "[4/4] 推送到 GitHub ..." -ForegroundColor Green
git push -u origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host "  X 推送失败。" -ForegroundColor Red
    Write-Host "    如果提示权限错误，检查 ~/.ssh/config 是否指向 ssh.github.com:443。" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "=== 完成 ===" -ForegroundColor Cyan
Write-Host "仓库地址: $($repo.html_url)" -ForegroundColor Green
Write-Host ""
Write-Host "提示: 建议再补一个仓库 topic 和 Release(附带 jar)，让 mod 更容易被搜到。" -ForegroundColor DarkGray
