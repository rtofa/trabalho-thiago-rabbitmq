$ErrorActionPreference = "SilentlyContinue"
$RootDir = Split-Path -Parent $PSScriptRoot
$pidFiles = Get-ChildItem "$RootDir\.pids\*.pid"

foreach ($f in $pidFiles) {
    $procId = Get-Content $f.FullName
    if ($procId) {
        Write-Host "Encerrando $($f.BaseName) (PID $procId)..." -ForegroundColor Yellow
        & taskkill.exe /PID $procId /T /F 2>&1 | Out-Null
    }
    Remove-Item $f.FullName -Force
}

$stragglers = Get-CimInstance Win32_Process -Filter "name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match "coop-producer|coop-consumer-" }
foreach ($s in $stragglers) {
    Stop-Process -Id $s.ProcessId -Force -ErrorAction SilentlyContinue
}

Write-Host "Todos os processos Java locais foram encerrados." -ForegroundColor Green
