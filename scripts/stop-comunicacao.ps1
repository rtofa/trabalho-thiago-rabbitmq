$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$pidFile = "$RootDir\.pids\comunicacao.pid"

if (Test-Path $pidFile) {
    $procId = Get-Content $pidFile
    if ($procId) {
        & taskkill.exe /PID $procId /T /F 2>&1 | Out-Null
        Write-Host "[PARADO] coop-consumer-comunicacao (PID $procId) interrompido." -ForegroundColor Yellow
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

# Garante que nenhuma instancia residual do comunicacao permaneca viva
$stragglers = Get-CimInstance Win32_Process -Filter "name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match "coop-consumer-comunicacao" }
foreach ($s in $stragglers) {
    Stop-Process -Id $s.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host "[PARADO] Processo residual coop-consumer-comunicacao (PID $($s.ProcessId)) encerrado." -ForegroundColor Yellow
}
