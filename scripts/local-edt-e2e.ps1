#Requires -Version 5.1
<#
.SYNOPSIS
    Giper-EDT-MCP: one-shot local EDT e2e loop (build -> redeploy -> boot EDT -> run e2e).

.DESCRIPTION
    Прогоняет весь локальный цикл против ТВОЕЙ установленной 1C:EDT одной командой:
      1) сборка плагина (source/compile.sh, по умолчанию без юнит-тестов — они уже в CI/Tier 1);
      2) остановка запущенной EDT/1С;
      3) (пере)установка свежесобранного плагина в EDT через p2 director (снимает "stale jar");
      4) запуск EDT с EDT_MCP_AUTO_START/EDT_MCP_IMPORT_PROJECTS — сервер сам поднимается на :PORT
         и импортирует три фикстурных проекта (TestConfiguration, tests, ExternalObjects);
      5) ожидание /health на :PORT;
      6) python tests/e2e/run_all.py --project TestConfiguration;
      7) (опц.) остановка EDT.

    Рассчитан на запуск ЛОКАЛЬНЫМ агентом (Cursor Remote Control) одной фразой.
    Пути не хардкодятся — всё через параметры/значения по умолчанию.

.PARAMETER EdtExe
    Путь к 1cedt.exe твоей (желательно НЕ из Program Files) копии EDT. Обязателен.

.PARAMETER LiveInfobase
    Включить live-infobase инструменты (debug/update_database/YAXUnit/...): выставляет
    EDT_MCP_LIVE_INFOBASE=1. Требует установленной платформы 1С, зарегистрированной ИБ и
    launch-конфигурации (см. -LaunchConfig). Без флага эти тесты пропускаются (как в CI).

.EXAMPLE
    pwsh -File scripts/local-edt-e2e.ps1 -EdtExe "D:\EDT\1c-edt-2026.2\1cedt.exe"

.EXAMPLE
    pwsh -File scripts/local-edt-e2e.ps1 -EdtExe "D:\EDT\...\1cedt.exe" -LiveInfobase -Filter yaxunit
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$EdtExe,

    # Рабочая область EDT (метаданные Eclipse). Проекты импортируются ПО ССЫЛКЕ и остаются в tests/.
    [string]$Workspace = (Join-Path $env:LOCALAPPDATA 'GiperEdtMcp\e2e-ws'),

    [int]$Port = 8765,

    # Подстрока-фильтр для run_all.py (--filter). Пусто = весь набор.
    [string]$Filter = '',

    # Прогнать live-infobase инструменты (нужна платформа 1С + ИБ + launch-конфиг).
    [switch]$LiveInfobase,
    [string]$LaunchConfig = 'TestConfiguration Thin Client',

    # Тулчейн сборки. Если mvn/JDK17 уже в PATH — можно не задавать.
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenHome = $env:MAVEN_HOME,
    [string]$BashExe = 'bash',            # Git Bash: bash.exe (для source/compile.sh)
    [string]$Python = 'python',

    [switch]$SkipBuild,                    # не пересобирать плагин
    [switch]$SkipInstall,                  # не переустанавливать в EDT (плагин уже стоит)
    [switch]$WithUnitTests,               # собрать С юнит-тестами (по умолчанию --skip-tests)
    [switch]$CleanWorkspace,              # очистить workspace перед стартом (полный переиндекс)
    [switch]$StopEdt,                      # остановить EDT после прогона (по умолчанию оставляем)
    [int]$ReadyTimeoutSec = 900           # ожидание /health (холодный первый индекс — долгий)
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Корень репозитория = родитель каталога scripts/.
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$FeatureIU = 'fm.giper.edt.mcp.server.feature.feature.group'
$OldFeatureIU = 'com.ditrix.edt.mcp.server.feature.feature.group'   # апстрим — удаляем, если стоит
$RepoP2 = Join-Path $RepoRoot 'mcp\repositories\fm.giper.edt.mcp.server.repository\target\repository'

function Info($m) { Write-Host "[giper-e2e] $m" -ForegroundColor Cyan }
function Warn($m) { Write-Host "[giper-e2e] $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "[giper-e2e] ОШИБКА: $m" -ForegroundColor Red; exit 1 }

if (-not (Test-Path $EdtExe)) { Die "не найден 1cedt.exe: $EdtExe" }
Info "Репозиторий : $RepoRoot"
Info "EDT         : $EdtExe"
Info "Workspace   : $Workspace"
Info "Порт        : $Port  | Live-infobase: $($LiveInfobase.IsPresent)"

# ── 1. Сборка плагина (source/compile.sh через Git Bash) ─────────────────────
if (-not $SkipBuild) {
    Info '1/6 Сборка плагина (source/compile.sh)...'
    $buildArgs = @('source/compile.sh')
    if (-not $WithUnitTests) { $buildArgs += '--skip-tests' }
    if ($JavaHome)  { $buildArgs += @('--java-home',  $JavaHome) }
    if ($MavenHome) { $buildArgs += @('--maven-home', $MavenHome) }
    Push-Location $RepoRoot
    try { & $BashExe @buildArgs; if ($LASTEXITCODE -ne 0) { Die "compile.sh упал (код $LASTEXITCODE)" } }
    finally { Pop-Location }
} else { Info '1/6 Сборка пропущена (-SkipBuild)' }

if (-not (Test-Path (Join-Path $RepoP2 'content.jar')) -and -not (Test-Path (Join-Path $RepoP2 'content.xml'))) {
    if (-not $SkipInstall) { Die "не найден p2-репозиторий плагина: $RepoP2 (сначала собери без -SkipBuild)" }
}

# ── 2. Остановить запущенную EDT / 1С (иначе p2 director не сможет писать) ────
Info '2/6 Останавливаю запущенную EDT...'
# 1cedt глушим всегда (нужно для p2 director и чистого запуска). 1С-клиенты (1cv8/1cv8c)
# трогаем только в live-режиме, чтобы не прибить постороннюю работу с 1С.
$procsToKill = @('1cedt')
if ($LiveInfobase) { $procsToKill += @('1cv8c', '1cv8') }
foreach ($proc in $procsToKill) {
    Get-Process -Name $proc -ErrorAction SilentlyContinue | ForEach-Object {
        Info "  kill $($_.ProcessName) (pid $($_.Id))"; Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
}
Start-Sleep -Seconds 2

# ── 3. Переустановка плагина в EDT (снятие stale-jar) ────────────────────────
if (-not $SkipInstall) {
    Info '3/6 Обновляю плагин в EDT через p2 director...'
    # Удалить апстримовый плагин com.ditrix, если он установлен (иначе два сервера подерутся за порт).
    & $EdtExe -nosplash -application org.eclipse.equinox.p2.director -uninstallIU $OldFeatureIU 2>$null | Out-Null
    $repoUri = ([System.Uri]$RepoP2).AbsoluteUri   # file:///D:/.../repository
    & $EdtExe -nosplash -consoleLog `
        -application org.eclipse.equinox.p2.director `
        -repository $repoUri `
        -installIU $FeatureIU `
        -profileProperties org.eclipse.update.reconcile=true
    if ($LASTEXITCODE -ne 0) { Die "p2 director (install $FeatureIU) упал (код $LASTEXITCODE)" }
} else { Info '3/6 Установка пропущена (-SkipInstall)' }

# ── 4. Запуск EDT с авто-стартом сервера и импортом фикстур ──────────────────
Info '4/6 Запускаю EDT (авто-старт MCP + импорт фикстур)...'
if ($CleanWorkspace -and (Test-Path $Workspace)) { Remove-Item -Recurse -Force $Workspace }
New-Item -ItemType Directory -Force -Path $Workspace | Out-Null

# EDT_MCP_IMPORT_PROJECTS разделяется через File.pathSeparator => на Windows это ';'.
$importDirs = @(
    (Join-Path $RepoRoot 'tests\TestConfiguration'),
    (Join-Path $RepoRoot 'tests\tests'),
    (Join-Path $RepoRoot 'tests\ExternalObjects')
) -join ';'

$env:EDT_MCP_AUTO_START = 'true'
$env:EDT_MCP_PORT = "$Port"
$env:EDT_MCP_IMPORT_PROJECTS = $importDirs
$env:EDT_MCP_DESTRUCTIVE_CONSENT = 'allow'   # чтобы деструктивные e2e не висли на диалоге подтверждения
if ($LiveInfobase) {
    $env:EDT_MCP_LIVE_INFOBASE = '1'
    $env:MCP_LIVE_LAUNCH_CONFIG = $LaunchConfig
    Info "  live-infobase ВКЛ, launch-конфиг: $LaunchConfig"
} else {
    $env:EDT_MCP_LIVE_INFOBASE = ''
}

# ВНИМАНИЕ: -vmargs НЕ передаём — иначе перезатрём блок eclipse.ini (Ignite/add-opens),
# и EDT не поднимется. Флаг рендера форм (-DnativeFormBufferedLayoutRender=true) при
# необходимости пропиши один раз в 1cedt.ini (см. README).
$edt = Start-Process -FilePath $EdtExe -ArgumentList @('-data', $Workspace) -PassThru
Info "  EDT PID: $($edt.Id)"

# ── 5. Ожидание готовности сервера ───────────────────────────────────────────
Info "5/6 Жду MCP /health на :$Port (до $ReadyTimeoutSec c)..."
$healthUrl = "http://127.0.0.1:$Port/health"
$deadline = (Get-Date).AddSeconds($ReadyTimeoutSec)
$up = $false
while ((Get-Date) -lt $deadline) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec 5
        if ($r.StatusCode -eq 200) { $up = $true; break }
    } catch { Start-Sleep -Seconds 5 }
}
if (-not $up) { Die "MCP-сервер не поднялся на :$Port за ${ReadyTimeoutSec}c (см. $Workspace\.metadata\.log)" }
Info '  MCP server UP.'

# ── 6. Прогон e2e ────────────────────────────────────────────────────────────
Info '6/6 Прогоняю tests/e2e/run_all.py...'
$env:MCP_PORT = "$Port"
$e2eArgs = @('tests/e2e/run_all.py', '--project', 'TestConfiguration',
             '--junit-xml', 'tests/e2e/e2e-results.xml')
if ($Filter) { $e2eArgs += @('--filter', $Filter) }
Push-Location $RepoRoot
try { & $Python @e2eArgs; $e2eCode = $LASTEXITCODE }
finally { Pop-Location }

if ($StopEdt) {
    Info "Останавливаю EDT (pid $($edt.Id))..."
    Stop-Process -Id $edt.Id -Force -ErrorAction SilentlyContinue
} else {
    Info "EDT оставлен запущенным (pid $($edt.Id)); отчёт: tests/e2e/e2e-results.xml"
}

if ($e2eCode -eq 0) { Info 'E2E: УСПЕХ.' } else { Warn "E2E: провал/пропуски (код $e2eCode) — см. отчёт и .metadata\.log" }
exit $e2eCode
