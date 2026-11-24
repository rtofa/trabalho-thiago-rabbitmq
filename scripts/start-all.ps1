param(
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

function Resolve-RealJava() {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return "$env:JAVA_HOME\bin\java.exe"
    }
    $jdkJava = Get-ChildItem "C:\Program Files\Java\jdk*\bin\java.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($jdkJava) {
        return $jdkJava.FullName
    }
    return "java"
}

$JavaExe = Resolve-RealJava

New-Item -ItemType Directory -Force -Path "$RootDir\.pids" | Out-Null
New-Item -ItemType Directory -Force -Path "$RootDir\logs" | Out-Null

Write-Host "==> Subindo RabbitMQ e PostgreSQL via Docker Compose..." -ForegroundColor Cyan
docker compose up -d --wait rabbitmq postgres

$modules = @(
    @{ Name = "producer";      Dir = "coop-producer";             Jar = "coop-producer-1.0.0.jar";             Port = 8180; Args = @() },
    @{ Name = "recepcao";      Dir = "coop-consumer-recepcao";    Jar = "coop-consumer-recepcao-1.0.0.jar";    Port = 8181; Args = @() },
    @{ Name = "comunicacao";   Dir = "coop-consumer-comunicacao"; Jar = "coop-consumer-comunicacao-1.0.0.jar"; Port = 8182; Args = @() },
    @{ Name = "qualidade";     Dir = "coop-consumer-qualidade";   Jar = "coop-consumer-qualidade-1.0.0.jar";   Port = 8183; Args = @() },
    @{ Name = "auditoria";     Dir = "coop-consumer-auditoria";   Jar = "coop-consumer-auditoria-1.0.0.jar";   Port = 8184; Args = @() },
    @{ Name = "indicadores-1"; Dir = "coop-consumer-indicadores"; Jar = "coop-consumer-indicadores-1.0.0.jar"; Port = 8185; Args = @("--server.port=8185", "--coop.instance-id=indicadores-1") },
    @{ Name = "indicadores-2"; Dir = "coop-consumer-indicadores"; Jar = "coop-consumer-indicadores-1.0.0.jar"; Port = 8186; Args = @("--server.port=8186", "--coop.instance-id=indicadores-2") }
)

$missingJar = $false
foreach ($m in $modules) {
    if (-not (Test-Path "$RootDir\$($m.Dir)\target\$($m.Jar)")) {
        $missingJar = $true
        break
    }
}

if ($Build -or $missingJar) {
    Write-Host "==> Compilando e empacotando os módulos Maven..." -ForegroundColor Cyan
    & "$RootDir\mvnw.cmd" clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Falha no build Maven."
    }
}

function Test-ServiceUp([int]$Port) {
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2
        if ($resp.StatusCode -ne 200) { return $false }
        $body = if ($resp.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($resp.Content) } else { [string]$resp.Content }
        return (($body | ConvertFrom-Json).status -eq "UP")
    } catch {
        return $false
    }
}

# Inicia o producer primeiro para garantir migração Flyway sem corrida e ordem limpa
foreach ($m in $modules) {
    $pidFile = "$RootDir\.pids\$($m.Name).pid"
    if (Test-Path $pidFile) {
        $existingPid = Get-Content $pidFile
        if ($existingPid -and (Get-Process -Id $existingPid -ErrorAction SilentlyContinue)) {
            Write-Host "    [SKIP] $($m.Name) já está em execução (PID $existingPid, porta $($m.Port))." -ForegroundColor Yellow
            continue
        }
    }

    $jarPath = "$RootDir\$($m.Dir)\target\$($m.Jar)"
    $outLog = "$RootDir\logs\$($m.Name).out.log"
    $errLog = "$RootDir\logs\$($m.Name).err.log"
    $argList = @("-jar", "`"$jarPath`"") + $m.Args

    Write-Host "    Iniciando $($m.Name) na porta $($m.Port)..." -ForegroundColor Green
    $proc = Start-Process -FilePath $JavaExe -ArgumentList $argList -WorkingDirectory $RootDir `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
    $proc.Id | Set-Content $pidFile

    if ($m.Name -eq "producer") {
        for ($i = 0; $i -lt 60; $i++) {
            if (Test-ServiceUp $m.Port) { break }
            Start-Sleep -Seconds 1
        }
    }
}

Write-Host "==> Aguardando todas as aplicações ficarem UP..." -ForegroundColor Cyan
foreach ($m in $modules) {
    $ready = $false
    for ($i = 0; $i -lt 90; $i++) {
        if (Test-ServiceUp $m.Port) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        throw "Serviço $($m.Name) na porta $($m.Port) não ficou UP a tempo. Verifique logs\$($m.Name).out.log e .err.log"
    }
    Write-Host "    [UP] $($m.Name) -> http://localhost:$($m.Port)" -ForegroundColor Green
}

Write-Host ""
Write-Host "Todos os 7 processos estão ativos! RabbitMQ UI: http://localhost:15672 (coop / coop)" -ForegroundColor Cyan
