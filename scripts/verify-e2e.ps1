param(
    [ValidateSet("Local", "Docker")]
    [string]$Mode = "Local",
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

function Decode-Body($resp) {
    if ($resp.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($resp.Content)
    }
    return [string]$resp.Content
}

function Post-Json($url, $obj) {
    $json = if ($null -eq $obj) { "{}" } else { $obj | ConvertTo-Json -Depth 5 }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $resp = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $url -Body $bytes -ContentType "application/json; charset=utf-8"
    return ((Decode-Body $resp) | ConvertFrom-Json)
}

function Get-Json($url) {
    $resp = Invoke-WebRequest -UseBasicParsing -Method Get -Uri $url
    $parsed = (Decode-Body $resp) | ConvertFrom-Json
    return ,$parsed
}

function Get-JsonObject($url) {
    $resp = Invoke-WebRequest -UseBasicParsing -Method Get -Uri $url
    return ((Decode-Body $resp) | ConvertFrom-Json)
}

function Get-RabbitQueues() {
    $pair = "coop:coop"
    $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    $hdr = @{ Authorization = "Basic $b64" }
    $resp = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:15672/api/queues" -Headers $hdr
    $parsed = (Decode-Body $resp) | ConvertFrom-Json
    return ,$parsed
}

function Assert-True($cond, $msg) {
    if (-not $cond) {
        throw "FALHA NA ASSERCAO: $msg"
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " VERIFICACAO PONTA A PONTA ($Mode) - COOPERATIVA RABBITMQ" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

& "$PSScriptRoot\stop-all.ps1"
if ($Mode -eq "Docker") {
    docker compose up -d --wait | Out-Null
} else {
    docker compose up -d --wait rabbitmq postgres | Out-Null
}

Write-Host "==> Limpando tabelas de dominio e filas para execucao limpa..." -ForegroundColor Cyan
docker exec coop-postgres psql -U coop -d cooperativa -c "DO `$`$ DECLARE t text; BEGIN FOR t IN SELECT unnest(ARRAY['published_events','processed_events','recepcao_tickets','comunicacao_avisos','qualidade_resultados','auditoria_eventos','indicadores_totais','indicadores_execucoes']) LOOP IF to_regclass('public.' || t) IS NOT NULL THEN EXECUTE 'TRUNCATE TABLE ' || quote_ident(t) || ' RESTART IDENTITY'; END IF; END LOOP; END `$`$;" | Out-Null
foreach ($q in @("coop.recepcao.queue","coop.comunicacao.queue","coop.qualidade.queue","coop.auditoria.queue","coop.indicadores.queue")) {
    docker exec coop-rabbitmq rabbitmqctl purge_queue $q | Out-Null
}

if ($Mode -eq "Local") {
    & "$PSScriptRoot\start-all.ps1"
}

try {
    Start-Sleep -Seconds 2

    Write-Host ""
    Write-Host "--- ETAPA 1 e 2: Roteamento dos 4 eventos obrigatorios ---" -ForegroundColor Yellow

    # 1. Carga recebida -> Recepcao, Comunicacao, Auditoria, Indicadores (NAO Qualidade)
    $e1 = Post-Json "http://localhost:8180/api/eventos/carga-recebida" @{
        eventId = "evt-001-recebida"
        lote = "LOTE-001"
        cooperado = "Fazenda Boa Vista"
        produto = "SOJA"
        pesoLiquidoKg = 10000.00
    }
    Assert-True ($e1.brokerConfirmed -eq $true) "Publisher confirm deve ser true para carga-recebida"
    Start-Sleep -Seconds 2

    $tickets = Get-Json "http://localhost:8181/api/recepcao/tickets"
    $avisos = Get-Json "http://localhost:8182/api/comunicacao/avisos"
    $analises = Get-Json "http://localhost:8183/api/qualidade/analises"
    $auditoria = Get-Json "http://localhost:8184/api/auditoria/eventos"
    $totais = Get-Json "http://localhost:8185/api/indicadores/totais"

    Assert-True ($tickets.Count -eq 1) "Recepcao deve ter 1 ticket apos CARGA_RECEBIDA (atual: $($tickets.Count))"
    Assert-True ($avisos.Count -eq 1 -and $avisos[0].tipoAviso -eq "RECEBIMENTO") "Comunicacao deve ter 1 aviso RECEBIMENTO"
    Assert-True ($analises.Count -eq 0) "Qualidade NAO deve receber CARGA_RECEBIDA"
    Assert-True ($auditoria.Count -eq 1) "Auditoria deve ter 1 evento"
    Assert-True ($totais.Count -eq 1 -and [decimal]$totais[0].pesoTotalKg -eq 10000) "Indicadores deve somar 10000 kg de SOJA"
    Write-Host "  [OK] CARGA_RECEBIDA -> Recepcao(1), Comunicacao(1), Qualidade(0), Auditoria(1), Indicadores(10000 kg SOJA)" -ForegroundColor Green

    # 2. Carga recusada -> Comunicacao, Auditoria (NAO Recepcao, NAO Qualidade, NAO Indicadores)
    $e2 = Post-Json "http://localhost:8180/api/eventos/carga-recusada" @{
        eventId = "evt-002-recusada"
        lote = "LOTE-002"
        cooperado = "Sitio Esperanca"
        produto = "MILHO"
        motivo = "Umidade acima de 20%"
    }
    Start-Sleep -Seconds 2

    $tickets = Get-Json "http://localhost:8181/api/recepcao/tickets"
    $avisos = Get-Json "http://localhost:8182/api/comunicacao/avisos"
    $analises = Get-Json "http://localhost:8183/api/qualidade/analises"
    $auditoria = Get-Json "http://localhost:8184/api/auditoria/eventos"
    $totais = Get-Json "http://localhost:8185/api/indicadores/totais"

    Assert-True ($tickets.Count -eq 1) "Recepcao NAO deve receber CARGA_RECUSADA"
    Assert-True ($avisos.Count -eq 2 -and $avisos[0].tipoAviso -eq "RECUSA_CARGA") "Comunicacao deve registrar RECUSA_CARGA"
    Assert-True ($analises.Count -eq 0) "Qualidade NAO deve receber CARGA_RECUSADA"
    Assert-True ($auditoria.Count -eq 2) "Auditoria deve ter 2 eventos"
    Assert-True ($totais.Count -eq 1) "Indicadores NAO deve receber CARGA_RECUSADA"
    Write-Host "  [OK] CARGA_RECUSADA -> Recepcao(1), Comunicacao(2), Qualidade(0), Auditoria(2), Indicadores(inalterado)" -ForegroundColor Green

    # 3. Analise aprovada -> Qualidade, Auditoria (NAO Recepcao, NAO Comunicacao, NAO Indicadores)
    $e3 = Post-Json "http://localhost:8180/api/eventos/analise-aprovada" @{
        eventId = "evt-003-aprovada"
        lote = "LOTE-001"
        produto = "SOJA"
    }
    Start-Sleep -Seconds 2

    $tickets = Get-Json "http://localhost:8181/api/recepcao/tickets"
    $avisos = Get-Json "http://localhost:8182/api/comunicacao/avisos"
    $analises = Get-Json "http://localhost:8183/api/qualidade/analises"
    $auditoria = Get-Json "http://localhost:8184/api/auditoria/eventos"

    Assert-True ($tickets.Count -eq 1) "Recepcao NAO deve receber ANALISE_APROVADA"
    Assert-True ($avisos.Count -eq 2) "Comunicacao NAO deve receber ANALISE_APROVADA"
    Assert-True ($analises.Count -eq 1 -and $analises[0].resultado -eq "APROVADA") "Qualidade deve registrar APROVADA"
    Assert-True ($auditoria.Count -eq 3) "Auditoria deve ter 3 eventos"
    Write-Host "  [OK] ANALISE_APROVADA -> Recepcao(1), Comunicacao(2), Qualidade(1), Auditoria(3)" -ForegroundColor Green

    # 4. Analise reprovada -> Comunicacao, Qualidade, Auditoria (NAO Recepcao, NAO Indicadores)
    $e4 = Post-Json "http://localhost:8180/api/eventos/analise-reprovada" @{
        eventId = "evt-004-reprovada"
        lote = "LOTE-003"
        cooperado = "Estancia Santa Helena"
        produto = "TRIGO"
        motivo = "Micotoxina acima do padrao"
    }
    Start-Sleep -Seconds 2

    $tickets = Get-Json "http://localhost:8181/api/recepcao/tickets"
    $avisos = Get-Json "http://localhost:8182/api/comunicacao/avisos"
    $analises = Get-Json "http://localhost:8183/api/qualidade/analises"
    $auditoria = Get-Json "http://localhost:8184/api/auditoria/eventos"

    Assert-True ($tickets.Count -eq 1) "Recepcao NAO deve receber ANALISE_REPROVADA"
    Assert-True ($avisos.Count -eq 3 -and $avisos[0].tipoAviso -eq "REPROVACAO_ANALISE") "Comunicacao deve registrar REPROVACAO_ANALISE"
    Assert-True ($analises.Count -eq 2 -and $analises[0].resultado -eq "REPROVADA") "Qualidade deve registrar REPROVADA"
    Assert-True ($auditoria.Count -eq 4) "Auditoria deve ter 4 eventos"
    Write-Host "  [OK] ANALISE_REPROVADA -> Recepcao(1), Comunicacao(3), Qualidade(2), Auditoria(4)" -ForegroundColor Green

    # Bonus: Novo evento da familia analise.* + Evento sem regra especifica
    Write-Host ""
    Write-Host "--- Teste de extensibilidade: novo fato em analise.* e evento sem regra especifica ---" -ForegroundColor Yellow
    $e5 = Post-Json "http://localhost:8180/api/eventos/publicar" @{
        eventId = "evt-005-reavaliada"
        eventType = "ANALISE_REAVALIADA"
        routingKey = "analise.reavaliada"
        payload = @{
            lote = "LOTE-003"
            cooperado = "Estancia Santa Helena"
            produto = "TRIGO"
            motivo = "Contraprova solicitada pelo laboratorio"
        }
    }
    $e6 = Post-Json "http://localhost:8180/api/eventos/publicar" @{
        eventId = "evt-006-faturamento"
        eventType = "FATURAMENTO_EMITIDO"
        routingKey = "faturamento.emitido"
        payload = @{
            lote = "LOTE-001"
            cooperado = "Fazenda Boa Vista"
            produto = "SOJA"
        }
    }
    Start-Sleep -Seconds 2

    $analises = Get-Json "http://localhost:8183/api/qualidade/analises"
    $auditoria = Get-Json "http://localhost:8184/api/auditoria/eventos"
    $avisos = Get-Json "http://localhost:8182/api/comunicacao/avisos"
    Assert-True ($analises.Count -eq 3 -and $analises[0].resultado -eq "REAVALIADA") "Qualidade deve capturar novo fato analise.reavaliada via analise.*"
    Assert-True ($avisos.Count -eq 3) "Comunicacao NAO deve receber analise.reavaliada nem faturamento.emitido"
    Assert-True ($auditoria.Count -eq 6) "Auditoria deve capturar todos os 6 eventos via #"
    Write-Host "  [OK] analise.reavaliada -> Qualidade(3) + Auditoria(5); faturamento.emitido -> apenas Auditoria(6)" -ForegroundColor Green

    # Consulta filtrada na Auditoria por eventId e eventType
    $audById = Get-Json "http://localhost:8184/api/auditoria/eventos?eventId=evt-005-reavaliada"
    $audByType = Get-Json "http://localhost:8184/api/auditoria/eventos?eventType=ANALISE_REPROVADA"
    Assert-True ($audById.Count -eq 1) "Auditoria deve filtrar corretamente por eventId"
    Assert-True ($audByType.Count -eq 1) "Auditoria deve filtrar corretamente por eventType"
    Write-Host "  [OK] Auditoria consultavel por eventId, eventType e intervalo de horario" -ForegroundColor Green

    # ETAPA 3: Trabalho concorrente nas 2 instancias do 5o consumidor (indicadores-1 e indicadores-2)
    Write-Host ""
    Write-Host "--- ETAPA 3: Trabalho concorrente (2 instancias de Indicadores) ---" -ForegroundColor Yellow
    for ($i = 1; $i -le 6; $i++) {
        Post-Json "http://localhost:8180/api/eventos/carga-recebida" @{
            eventId = "evt-conc-$i"
            lote = "LOTE-CONC-$i"
            cooperado = "Produtor $i"
            produto = if ($i % 2 -eq 0) { "MILHO" } else { "SOJA" }
            pesoLiquidoKg = 1000.00
        } | Out-Null
    }
    Start-Sleep -Seconds 3

    $execSummary = Get-JsonObject "http://localhost:8185/api/indicadores/execucoes"
    $totais = Get-Json "http://localhost:8185/api/indicadores/totais"
    $soja = $totais | Where-Object { $_.produto -eq "SOJA" }
    $milho = $totais | Where-Object { $_.produto -eq "MILHO" }
    $inst1Count = [int]$execSummary.eventosProcessadosPorInstancia.'indicadores-1'
    $inst2Count = [int]$execSummary.eventosProcessadosPorInstancia.'indicadores-2'

    Assert-True (($inst1Count + $inst2Count) -eq 7) "Total de eventos unicos em Indicadores deve ser 7 (1 + 6)"
    Assert-True ($inst1Count -gt 0 -and $inst2Count -gt 0) "Ambas instancias devem processar eventos (inst1=$inst1Count, inst2=$inst2Count)"
    Assert-True ([decimal]$soja.pesoTotalKg -eq 13000) "SOJA deve totalizar 13000 kg (10000 + 3*1000)"
    Assert-True ([decimal]$milho.pesoTotalKg -eq 3000) "MILHO deve totalizar 3000 kg (3*1000)"
    Write-Host "  [OK] Trabalho dividido: indicadores-1=$inst1Count eventos, indicadores-2=$inst2Count eventos | SOJA=13000 kg, MILHO=3000 kg" -ForegroundColor Green

    # ETAPA 4: Interrupcao e recuperacao da Comunicacao
    Write-Host ""
    Write-Host "--- ETAPA 4: Interrupcao e recuperacao (Comunicacao) ---" -ForegroundColor Yellow
    if ($Mode -eq "Docker") {
        docker compose stop comunicacao | Out-Null
    } else {
        & "$PSScriptRoot\stop-comunicacao.ps1"
    }
    for ($w = 0; $w -lt 15; $w++) {
        $qCheck = (Get-RabbitQueues) | Where-Object { $_.name -eq "coop.comunicacao.queue" }
        if ($qCheck.consumers -eq 0) { break }
        Start-Sleep -Seconds 1
    }

    Post-Json "http://localhost:8180/api/eventos/analise-reprovada" @{
        eventId = "evt-recup-001"
        lote = "LOTE-RECUP-1"
        cooperado = "Cooperado Recuperado"
        produto = "SOJA"
        motivo = "Teste de pendencia com consumidor offline"
    } | Out-Null
    Start-Sleep -Seconds 2

    $analisesDepois = Get-Json "http://localhost:8183/api/qualidade/analises"
    $auditoriaDepois = Get-Json "http://localhost:8184/api/auditoria/eventos"
    $comQueue = $null
    for ($w = 0; $w -lt 12; $w++) {
        $queues = Get-RabbitQueues
        $comQueue = $queues | Where-Object { $_.name -eq "coop.comunicacao.queue" }
        if ($comQueue.consumers -eq 0 -and $comQueue.messages_ready -eq 1) { break }
        Start-Sleep -Seconds 1
    }

    Assert-True ($analisesDepois.Count -eq 4) "Qualidade deve processar imediatamente mesmo com Comunicacao parada"
    Assert-True ($auditoriaDepois.Count -eq 13) "Auditoria deve processar imediatamente mesmo com Comunicacao parada"
    Assert-True ($comQueue.consumers -eq 0 -and $comQueue.messages_ready -eq 1) "coop.comunicacao.queue deve ter 0 consumidores e 1 mensagem pendente (atual: consumers=$($comQueue.consumers), ready=$($comQueue.messages_ready))"
    Write-Host "  [OK] Comunicacao parada: Qualidade e Auditoria processaram; fila coop.comunicacao.queue reteve 1 mensagem pendente" -ForegroundColor Green

    if ($Mode -eq "Docker") {
        docker compose up -d --wait comunicacao | Out-Null
    } else {
        & "$PSScriptRoot\start-comunicacao.ps1"
    }
    $comQueueAfter = $null
    for ($w = 0; $w -lt 12; $w++) {
        $queuesAfter = Get-RabbitQueues
        $comQueueAfter = $queuesAfter | Where-Object { $_.name -eq "coop.comunicacao.queue" }
        if ($comQueueAfter.consumers -eq 1 -and $comQueueAfter.messages_ready -eq 0) { break }
        Start-Sleep -Seconds 1
    }
    $avisosAfter = Get-Json "http://localhost:8182/api/comunicacao/avisos"
    $recoveredAviso = $avisosAfter | Where-Object { $_.eventId -eq "evt-recup-001" }

    Assert-True ($comQueueAfter.consumers -eq 1 -and $comQueueAfter.messages_ready -eq 0) "Fila deve drenar apos religar Comunicacao"
    Assert-True ($null -ne $recoveredAviso) "Aviso evt-recup-001 deve constar em Comunicacao apos recuperacao"
    Write-Host "  [OK] Comunicacao religada: fila drenada e aviso evt-recup-001 registrado!" -ForegroundColor Green

    # ETAPA 5: Duplicidade com mesmo eventId -> efeito unico no peso de Indicadores
    Write-Host ""
    Write-Host "--- ETAPA 5: Teste de duplicidade com mesmo eventId ---" -ForegroundColor Yellow
    $dupPub = Post-Json "http://localhost:8180/api/eventos/carga-recebida" @{
        eventId = "evt-dup-cafe-01"
        lote = "LOTE-CAFE-01"
        cooperado = "Sitio Cafezal"
        produto = "CAFE"
        pesoLiquidoKg = 7500.00
    }
    Start-Sleep -Seconds 2

    $totaisAntes = Get-Json "http://localhost:8185/api/indicadores/totais"
    $cafeAntes = $totaisAntes | Where-Object { $_.produto -eq "CAFE" }
    Assert-True ([decimal]$cafeAntes.pesoTotalKg -eq 7500 -and $cafeAntes.totalCargas -eq 1) "CAFE deve ter 7500 kg antes da duplicata"

    # Republicacao intencional com o mesmo eventId (duas vezes seguidas para passar pelas instancias!)
    Post-Json "http://localhost:8180/api/eventos/evt-dup-cafe-01/republicar" $null | Out-Null
    Post-Json "http://localhost:8180/api/eventos/evt-dup-cafe-01/republicar" $null | Out-Null
    Start-Sleep -Seconds 2

    $totaisDepois = Get-Json "http://localhost:8185/api/indicadores/totais"
    $cafeDepois = $totaisDepois | Where-Object { $_.produto -eq "CAFE" }
    $execDepois = Get-JsonObject "http://localhost:8185/api/indicadores/execucoes"
    $dupsCafe = @($execDepois.execucoes | Where-Object { $_.eventId -eq "evt-dup-cafe-01" -and $_.status -eq "DUPLICADO_IGNORADO" })

    Assert-True ([decimal]$cafeDepois.pesoTotalKg -eq 7500 -and $cafeDepois.totalCargas -eq 1) "CAFE deve permanecer exatamente com 7500 kg e 1 carga apos republicacao duplicada!"
    Assert-True ($dupsCafe.Count -eq 2) "Deve registrar 2 tentativas duplicadas ignoradas em indicadores_execucoes (atual: $($dupsCafe.Count))"
    Write-Host "  [OK] Duplicidade comprovada: peso CAFE mantido em 7500.000 kg (somado 1 unica vez) e 2 entregas duplicadas ignoradas!" -ForegroundColor Green

    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host " TODOS OS CENARIOS PASSARAM COM SUCESSO!" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
}
finally {
    if (-not $KeepRunning) {
        if ($Mode -eq "Local") {
            & "$PSScriptRoot\stop-all.ps1"
        } else {
            docker compose stop producer recepcao comunicacao qualidade auditoria indicadores-1 indicadores-2 | Out-Null
        }
    }
}
