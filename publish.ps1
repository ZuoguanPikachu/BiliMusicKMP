#Requires -Version 7.0
<#
.SYNOPSIS
    打包发布产物：Android APK + 桌面端 MSI，并按版本号复制到 composeApp\release。

.DESCRIPTION
    版本号取自 gradle.properties 的 app.version，不在这里重复维护。
    Gradle 负责真正的构建，本脚本只做三件事：跑任务、把产物按版本号收拢、打印清单。

    APK 默认用 Debug：仓库里没有配置签名，Release 版产物是 *-unsigned.apk，手机装不上。
    真要出 Release 包，要么先补 signingConfig，要么手动签名后用 -ApkKind Release 构建。

.PARAMETER ApkKind
    Debug（默认，调试证书签名，可直接安装）或 Release（当前会得到未签名 APK）。

.PARAMETER SkipApk
    只打 MSI，不碰 Android。

.PARAMETER SkipMsi
    只打 APK，跳过桌面安装包（没装 WiX、或只想快速验证时用）。

.PARAMETER Clean
    构建前先跑 :composeApp:clean，全量重来。会连带删掉 jpackage 的运行时镜像和桌面端产物，
    耗时会明显变长。

.PARAMETER OutputDir
    产物输出目录，默认 composeApp\release（已在 .gitignore 里）。相对路径按仓库根目录解析。

.EXAMPLE
    .\publish.ps1
    打 Debug APK + MSI，产物丢进 composeApp\release。

.EXAMPLE
    .\publish.ps1 -SkipMsi
    只出 APK，适合改完 Android 端快速验一下。

.EXAMPLE
    .\publish.ps1 -ApkKind Release -Clean
    全量重来并出 Release 包（未签名，需要自己再签）。
#>
[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string]$ApkKind = 'Debug',

    [switch]$SkipApk,

    [switch]$SkipMsi,

    [switch]$Clean,

    [string]$OutputDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 基础路径
# ---------------------------------------------------------------------------
$repoRoot = Split-Path -Parent $PSCommandPath
$gradlew = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "找不到 gradlew.bat（$gradlew）。publish.ps1 需要放在仓库根目录。"
}

# ---------------------------------------------------------------------------
# 版本号：只认 gradle.properties，避免脚本里再抄一份
# ---------------------------------------------------------------------------
$propsPath = Join-Path $repoRoot 'gradle.properties'
$propsText = Get-Content -LiteralPath $propsPath -Raw
$versionMatch = [regex]::Match($propsText, '(?m)^\s*app\.version\s*=\s*(\S+)\s*$')
if (-not $versionMatch.Success) {
    throw "gradle.properties 里没有 app.version，无法确定版本号。"
}
$version = $versionMatch.Groups[1].Value

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot 'composeApp\release'
}
elseif (-not [System.IO.Path]::IsPathRooted($OutputDir)) {
    $OutputDir = Join-Path $repoRoot $OutputDir
}

# ---------------------------------------------------------------------------
# 跑 Gradle：失败就带着任务名抛出来，不要静默继续
# ---------------------------------------------------------------------------
function Invoke-GradleTask {
    param([Parameter(Mandatory)][string[]]$Tasks)

    $line = $Tasks -join ' '
    Write-Host "==> .\gradlew.bat $line" -ForegroundColor Cyan

    & $gradlew @Tasks '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle 构建失败（exit $LASTEXITCODE）：$line。上面是 Gradle 的输出；更详细的日志在 build\reports 与 composeApp\build\compose\logs。"
    }
}

# ---------------------------------------------------------------------------
# 构前检查
# ---------------------------------------------------------------------------
Write-Host "BiliMusic 打包：版本 $version" -ForegroundColor Green

# 工作区脏了就提醒一句：打出来的包和仓库 HEAD 对不上，事后很难判断包是哪份代码
try {
    $dirty = & git -C $repoRoot status --porcelain 2>$null
    if ($dirty) {
        Write-Warning '工作区有未提交的改动，打出来的包与仓库 HEAD 不一致。'
    }
} catch {
    Write-Verbose '跳过 git 状态检查（没装 git 或不在仓库里）。'
}

$buildApk = -not $SkipApk
$buildMsi = -not $SkipMsi
if (-not $buildApk -and -not $buildMsi) {
    throw '两个目标都被跳过了，没有可构建的内容。'
}

# MSI 由 jpackage 调 WiX 生成：先把插件自动下载的 WiX 塞进 PATH，
# 免得它没被 jpackage 找到（首次构建需要联网下载 WiX，落在 build\wix311）
$wixDir = Join-Path $repoRoot 'build\wix311'
if ($buildMsi) {
    if (Test-Path -LiteralPath (Join-Path $wixDir 'light.exe')) {
        $env:PATH = "$wixDir;$env:PATH"
        Write-Verbose "已把 $wixDir 加入 PATH"
    }
    else {
        $onPath = Get-Command light.exe -ErrorAction SilentlyContinue
        if (-not $onPath) {
            Write-Warning '没找到 WiX（light.exe）。首次打 MSI 时 Compose 插件会自动下载 WiX 3.11 到 build\wix311，需要联网；也可以手动安装 WiX 3.14 并加入 PATH。'
        }
    }

    # jpackage 的 MSI 版本只接受数字，2.0.3 这种没问题，2.0.3-beta 会直接失败
    if ($version -notmatch '^\d+(\.\d+){0,2}$') {
        Write-Warning "MSI 版本号要求纯数字（x.y.z），当前 app.version=$version 可能被 jpackage 拒绝；可以先 -SkipMsi 出 APK。"
    }
}

if ($buildApk -and $ApkKind -eq 'Release') {
    Write-Warning '仓库没有配置签名，Release APK 是未签名的，装不上设备；要能装请用 -ApkKind Debug，或先补 signingConfig。'
}

# ---------------------------------------------------------------------------
# 构建
# ---------------------------------------------------------------------------
$tasks = [System.Collections.Generic.List[string]]::new()
if ($Clean) {
    $tasks.Add(':composeApp:clean')
}
if ($buildApk) {
    $tasks.Add($(if ($ApkKind -eq 'Release') { ':composeApp:assembleRelease' } else { ':composeApp:assembleDebug' }))
}
if ($buildMsi) {
    $tasks.Add(':composeApp:packageMsi')
}

$started = Get-Date
Invoke-GradleTask -Tasks $tasks.ToArray()
$elapsed = (Get-Date) - $started

# ---------------------------------------------------------------------------
# 收集产物：用最近修改的那个，避免 clean 过期的旧文件混进来
# ---------------------------------------------------------------------------
function Find-LatestArtifact {
    param(
        [Parameter(Mandatory)][string]$Directory,
        [Parameter(Mandatory)][string]$Filter
    )

    if (-not (Test-Path -LiteralPath $Directory)) { return $null }

    Get-ChildItem -LiteralPath $Directory -Filter $Filter -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

$artifacts = [System.Collections.Generic.List[object]]::new()

if ($buildApk) {
    $apkDir = Join-Path $repoRoot "composeApp\build\outputs\apk\$($ApkKind.ToLowerInvariant())"
    $apk = Find-LatestArtifact -Directory $apkDir -Filter '*.apk'
    if (-not $apk) {
        throw "没找到 APK，预期的输出目录：$apkDir"
    }

    $artifacts.Add([pscustomobject]@{
            Kind = 'APK'
            File = $apk
            TargetName = if ($apk.Name -match 'unsigned') {
                "BiliMusic-$version-unsigned.apk"
            }
            else {
                "BiliMusic-$version.apk"
            }
        })
}

if ($buildMsi) {
    $msiDir = Join-Path $repoRoot 'composeApp\build\compose\binaries\main\msi'
    $msi = Find-LatestArtifact -Directory $msiDir -Filter '*.msi'
    if (-not $msi) {
        throw "没找到 MSI，预期的输出目录：$msiDir"
    }

    $artifacts.Add([pscustomobject]@{
            Kind = 'MSI'
            File = $msi
            TargetName = "BiliMusic-$version.msi"
        })
}

# ---------------------------------------------------------------------------
# 收拢到输出目录
# ---------------------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$rows = foreach ($artifact in $artifacts) {
    $target = Join-Path $OutputDir $artifact.TargetName
    Copy-Item -LiteralPath $artifact.File.FullName -Destination $target -Force

    [pscustomobject]@{
        类型 = $artifact.Kind
        文件 = $target
        大小 = '{0:N1} MB' -f ((Get-Item -LiteralPath $target).Length / 1MB)
    }
}

# ---------------------------------------------------------------------------
# 清单
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host "构建完成，用时 $([int]$elapsed.TotalSeconds) 秒" -ForegroundColor Green
Write-Host "版本 $version    输出目录 $OutputDir"
$rows | Format-Table -AutoSize | Out-String | Write-Host

if ($buildApk -and $ApkKind -eq 'Release') {
    Write-Warning "Release APK 未签名：$OutputDir\BiliMusic-$version-unsigned.apk，需要自己签名后才能安装。"
}
elseif ($buildApk) {
    Write-Host 'APK 用调试证书签名，可直接安装；对外发布前建议补 Release 签名。' -ForegroundColor DarkGray
}

if ($buildMsi) {
    Write-Host 'MSI 需要 Windows 10/11 与 WiX（或插件的自动下载），安装时会按用户所选目录落盘。' -ForegroundColor DarkGray
}
