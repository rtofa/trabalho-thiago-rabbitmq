$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$pidFile = "$RootDir\.pids\comunicacao.pid"
$jarPath = "$RootDir\coop-consumer-comunicacao\target\coop-consumer-comunicacao-1.0.0.jar"

if (Test-Path "$RootDir\.env") {
    Get-Content "$RootDir\.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            if (-not [Environment]::GetEnvironmentVariable($key, "Process")) {
                [Environment]::SetEnvironmentVariable($key, $val, "Process")
            }
        }
    }
}

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

New-Item -ItemType Directory -Force -Path "$RootDir\.pids" | Out-Null
New-Item -ItemType Directory -Force -Path "$RootDir\logs" | Out-Null

if (Test-Path $pidFile) {
    $existingPid = Get-Content $pidFile
    if ($existingPid -and (Get-Process -Id $existingPid -ErrorAction SilentlyContinue)) {
        Write-Host "coop-consumer-comunicacao já está em execução (PID $existingPid)." -ForegroundColor Yellow
        exit 0
    }
}

$JavaExe = Resolve-RealJava
$outLog = "$RootDir\logs\comunicacao.out.log"
$errLog = "$RootDir\logs\comunicacao.err.log"
$proc = Start-Process -FilePath $JavaExe -ArgumentList @("-jar", "`"$jarPath`"") -WorkingDirectory $RootDir `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
$proc.Id | Set-Content $pidFile

$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:8182/actuator/health" -TimeoutSec 2
        $body = if ($resp.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($resp.Content) } else { [string]$resp.Content }
        if ($resp.StatusCode -eq 200 -and (($body | ConvertFrom-Json).status -eq "UP")) {
            $ready = $true
            break
        }
    } catch {}
    Start-Sleep -Seconds 1
}

if (-not $ready) {
    throw "coop-consumer-comunicacao não ficou UP. Veja logs\comunicacao.out.log"
}

Write-Host "[RELIGADO] coop-consumer-comunicacao ativo novamente em http://localhost:8182 (PID $($proc.Id))." -ForegroundColor Green
