const state = {
  autoRefreshTimer: null,
  lastPublishedEventId: null
};

function esc(value) {
  if (value === null || value === undefined) return "";
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function shortId(id) {
  if (!id) return "";
  return id.length > 18 ? id.slice(0, 18) + "..." : id;
}

function formatNum(num) {
  if (num === null || num === undefined) return "—";
  return Number(num).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 3 });
}

function formatTime(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("pt-BR");
}

function showFeedback(text, isError = false) {
  const el = document.getElementById("feedbackBanner");
  el.textContent = text;
  el.className = "feedback " + (isError ? "err" : "ok");
}

function buildPayloadRequest() {
  const body = {};
  const eventId = document.getElementById("inputEventId").value.trim();
  const lote = document.getElementById("inputLote").value.trim();
  const cooperado = document.getElementById("inputCooperado").value.trim();
  const produto = document.getElementById("inputProduto").value.trim();
  const peso = document.getElementById("inputPeso").value.trim();
  const motivo = document.getElementById("inputMotivo").value.trim();

  if (eventId) body.eventId = eventId;
  if (lote) body.lote = lote;
  if (cooperado) body.cooperado = cooperado;
  if (produto) body.produto = produto;
  if (peso) body.pesoLiquidoKg = Number(peso);
  if (motivo) body.motivo = motivo;
  return body;
}

function updateRoutingPreview(routingKey) {
  document.getElementById("routingPreviewLabel").textContent = routingKey || "Selecione/dispare um evento";

  const matches = {
    recepcao: routingKey === "carga.recebida",
    comunicacao: ["carga.recebida", "carga.recusada", "analise.reprovada"].includes(routingKey),
    qualidade: typeof routingKey === "string" && /^analise\.[^.]+$/.test(routingKey),
    auditoria: Boolean(routingKey),
    indicadores: routingKey === "carga.recebida"
  };

  document.querySelectorAll("#routingMatrix tbody tr").forEach(row => {
    const key = row.dataset.consumer;
    const flagCell = row.querySelector(".matrix-flag");
    const yes = Boolean(matches[key]);
    row.className = routingKey ? (yes ? "match-yes" : "match-no") : "";
    flagCell.innerHTML = routingKey
      ? (yes ? '<span class="badge badge-ok">RECEBE</span>' : '<span class="badge badge-neutral">NÃO</span>')
      : "—";
  });
}

async function publishDomainEvent(endpoint, rk) {
  try {
    const resp = await fetch(`/api/eventos/${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(buildPayloadRequest())
    });
    const data = await resp.json();
    if (!resp.ok) {
      throw new Error(data.message || `Erro HTTP ${resp.status}`);
    }
    handlePublishResult(data, rk);
  } catch (err) {
    showFeedback("Erro ao publicar evento: " + err.message, true);
  }
}

async function publishCustomEvent() {
  const rk = document.getElementById("customRoutingKey").value.trim();
  const type = document.getElementById("customEventType").value.trim();
  if (!rk) {
    showFeedback("Informe a routingKey para o evento customizado.", true);
    return;
  }

  const req = buildPayloadRequest();
  const payload = {
    lote: req.lote || null,
    cooperado: req.cooperado || null,
    produto: req.produto || null,
    pesoLiquidoKg: req.pesoLiquidoKg ?? null,
    motivo: req.motivo || "Evento customizado disparado pelo painel",
    detalhes: { origem: "painel-web" }
  };

  try {
    const resp = await fetch("/api/eventos/publicar", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        eventId: req.eventId || null,
        eventType: type || null,
        routingKey: rk,
        payload
      })
    });
    const data = await resp.json();
    if (!resp.ok) {
      throw new Error(data.message || `Erro HTTP ${resp.status}`);
    }
    handlePublishResult(data, data.event.routingKey);
  } catch (err) {
    showFeedback("Erro ao publicar evento customizado: " + err.message, true);
  }
}

async function publishConcurrentBatch() {
  const products = ["SOJA", "MILHO", "TRIGO", "CAFE", "SOJA", "MILHO"];
  let lastData = null;
  try {
    for (let i = 0; i < products.length; i++) {
      const resp = await fetch("/api/eventos/carga-recebida", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          cooperado: `Produtor Lote ${i + 1}`,
          produto: products[i],
          pesoLiquidoKg: 1000
        })
      });
      lastData = await resp.json();
    }
    if (lastData) {
      handlePublishResult(lastData, "carga.recebida");
    }
    showFeedback("6 eventos distintos de carga recebida publicados para divisão entre indicadores-1 e indicadores-2.");
  } catch (err) {
    showFeedback("Erro ao publicar lote concorrente: " + err.message, true);
  }
}

async function republishSelectedEvent() {
  const selectedId = document.getElementById("replaySelect").value || state.lastPublishedEventId;
  if (!selectedId) {
    showFeedback("Publique ou selecione um evento antes de testar duplicidade.", true);
    return;
  }

  try {
    const resp = await fetch(`/api/eventos/${encodeURIComponent(selectedId)}/republicar`, {
      method: "POST"
    });
    const data = await resp.json();
    if (!resp.ok) {
      throw new Error(data.message || `Erro HTTP ${resp.status}`);
    }
    handlePublishResult(data, data.event.routingKey);
  } catch (err) {
    showFeedback("Erro ao republicar evento: " + err.message, true);
  }
}

function handlePublishResult(data, routingKey) {
  state.lastPublishedEventId = data.event.eventId;
  document.getElementById("lastJsonBox").textContent = JSON.stringify(data, null, 2);
  updateRoutingPreview(routingKey || data.event.routingKey);
  showFeedback(
    `${data.message} (eventId=${data.event.eventId}, confirm=${data.brokerConfirmed})`
  );
  setTimeout(refreshState, 350);
}

function renderStatusStrip(servicos) {
  const labels = {
    producer: "Produtor :8180",
    recepcao: "1. Recepção :8181",
    comunicacao: "2. Comunicação :8182",
    qualidade: "3. Qualidade :8183",
    auditoria: "4. Auditoria :8184",
    "indicadores-1": "5a. Indicadores-1 :8185",
    "indicadores-2": "5b. Indicadores-2 :8186"
  };
  const strip = document.getElementById("statusStrip");
  strip.innerHTML = Object.entries(labels).map(([key, label]) => {
    const up = Boolean(servicos[key]);
    return `<span class="status-pill ${up ? "up" : "down"}"><span class="dot"></span>${esc(label)}: ${up ? "UP" : "OFFLINE"}</span>`;
  }).join("");
}

function renderQueues(filas) {
  const tbody = document.getElementById("queuesTableBody");
  if (!filas || filas.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="muted">API de gerenciamento RabbitMQ indisponível ou sem filas.</td></tr>';
    return;
  }
  tbody.innerHTML = filas.map(q => {
    const highlightPending = q.messagesReady > 0 ? ' class="badge badge-warn"' : "";
    const noConsumers = !q.name.endsWith(".dlq") && q.consumers === 0 ? ' class="badge badge-err"' : "";
    return `
      <tr>
        <td class="mono">${esc(q.name)}</td>
        <td><span${noConsumers}>${q.consumers}</span></td>
        <td><span${highlightPending}>${q.messagesReady}</span></td>
        <td>${q.messagesUnacknowledged}</td>
        <td>${q.durable ? "Sim" : "Não"}</td>
      </tr>
    `;
  }).join("");
}

function renderRecepcao(online, tickets) {
  const badge = document.getElementById("badgeRecepcao");
  badge.className = "badge " + (online ? "badge-ok" : "badge-err");
  badge.textContent = online ? `UP (${tickets.length} tickets)` : "OFFLINE";

  const tbody = document.getElementById("recepcaoBody");
  if (!tickets.length) {
    tbody.innerHTML = '<tr><td colspan="5" class="muted">Nenhum ticket registrado.</td></tr>';
    return;
  }
  tbody.innerHTML = tickets.map(t => `
    <tr>
      <td><strong>${esc(t.lote)}</strong></td>
      <td>${esc(t.produto)}</td>
      <td>${formatNum(t.pesoLiquidoKg)}</td>
      <td>${esc(t.cooperado)}</td>
      <td class="mono" title="${esc(t.eventId)}">${esc(shortId(t.eventId))}</td>
    </tr>
  `).join("");
}

function renderComunicacao(online, avisos) {
  const badge = document.getElementById("badgeComunicacao");
  badge.className = "badge " + (online ? "badge-ok" : "badge-err");
  badge.textContent = online ? `UP (${avisos.length} avisos)` : "OFFLINE";

  const alert = document.getElementById("comunicacaoOfflineAlert");
  alert.classList.toggle("hidden", online);

  const tbody = document.getElementById("comunicacaoBody");
  if (!avisos.length) {
    tbody.innerHTML = `<tr><td colspan="5" class="muted">${online ? "Nenhum aviso registrado." : "Consumidor desligado no momento."}</td></tr>`;
    return;
  }
  tbody.innerHTML = avisos.map(a => `
    <tr>
      <td><span class="badge badge-info">${esc(a.tipoAviso)}</span></td>
      <td><strong>${esc(a.lote)}</strong></td>
      <td>${esc(a.cooperado)}</td>
      <td>${esc(a.mensagem)}</td>
      <td class="mono" title="${esc(a.eventId)}">${esc(shortId(a.eventId))}</td>
    </tr>
  `).join("");
}

function renderQualidade(online, analises) {
  const badge = document.getElementById("badgeQualidade");
  badge.className = "badge " + (online ? "badge-ok" : "badge-err");
  badge.textContent = online ? `UP (${analises.length} análises)` : "OFFLINE";

  const tbody = document.getElementById("qualidadeBody");
  if (!analises.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="muted">Nenhuma análise registrada.</td></tr>';
    return;
  }
  tbody.innerHTML = analises.map(q => {
    const cls = q.resultado === "APROVADA" ? "badge-ok" : (q.resultado === "REPROVADA" ? "badge-err" : "badge-warn");
    return `
      <tr>
        <td><span class="badge ${cls}">${esc(q.resultado)}</span></td>
        <td class="mono">${esc(q.routingKey)}</td>
        <td><strong>${esc(q.lote)}</strong></td>
        <td>${esc(q.produto)}</td>
        <td>${esc(q.motivo || "—")}</td>
        <td class="mono" title="${esc(q.eventId)}">${esc(shortId(q.eventId))}</td>
      </tr>
    `;
  }).join("");
}

function renderIndicadores(ind1Up, ind2Up, totais, execData) {
  const badge = document.getElementById("badgeIndicadores");
  const countUp = (ind1Up ? 1 : 0) + (ind2Up ? 1 : 0);
  badge.className = "badge " + (countUp === 2 ? "badge-ok" : (countUp === 1 ? "badge-warn" : "badge-err"));
  badge.textContent = `${countUp}/2 instâncias UP`;

  const totaisGrid = document.getElementById("indicadoresTotaisGrid");
  if (!totais.length) {
    totaisGrid.innerHTML = '<span class="muted">Nenhum peso contabilizado ainda.</span>';
  } else {
    totaisGrid.innerHTML = totais.map(t => `
      <div class="total-chip">
        <strong>${esc(t.produto)}: ${formatNum(t.pesoTotalKg)} kg</strong>
        <span>${t.totalCargas} carga(s) únicas</span>
      </div>
    `).join("");
  }

  const dist = execData?.eventosProcessadosPorInstancia || {};
  const distBar = document.getElementById("distribuicaoBar");
  const distEntries = Object.entries(dist);
  distBar.innerHTML = distEntries.length
    ? distEntries.map(([inst, qtd]) => `<span class="badge badge-info">${esc(inst)}: ${qtd} processado(s)</span>`).join("")
    : '<span class="muted">Aguardando processamento concorrente...</span>';

  const execs = execData?.execucoes || [];
  const tbody = document.getElementById("indicadoresExecucoesBody");
  if (!execs.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="muted">Sem execuções registradas.</td></tr>';
    return;
  }
  tbody.innerHTML = execs.map(e => {
    const dup = e.status === "DUPLICADO_IGNORADO";
    return `
      <tr>
        <td><strong>${esc(e.instanceId)}</strong></td>
        <td><span class="badge ${dup ? "badge-warn" : "badge-ok"}">${esc(e.status)}</span></td>
        <td>${esc(e.lote)}</td>
        <td>${esc(e.produto)}</td>
        <td>${formatNum(e.pesoLiquidoKg)}</td>
        <td class="mono" title="${esc(e.eventId)}">${esc(shortId(e.eventId))}</td>
      </tr>
    `;
  }).join("");
}

function renderAuditoria(online, eventos) {
  const badge = document.getElementById("badgeAuditoria");
  badge.className = "badge " + (online ? "badge-ok" : "badge-err");
  badge.textContent = online ? `UP (${eventos.length} registros)` : "OFFLINE";

  const tbody = document.getElementById("auditoriaBody");
  if (!eventos.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="muted">Nenhum evento auditado para os filtros atuais.</td></tr>';
    return;
  }
  tbody.innerHTML = eventos.map(a => `
    <tr>
      <td class="mono">${esc(a.eventId)}</td>
      <td><span class="badge badge-neutral">${esc(a.eventType)}</span></td>
      <td class="mono">${esc(a.routingKey)}</td>
      <td>${formatTime(a.occurredAt)}</td>
      <td>${a.deliveryCount}</td>
      <td class="mono">${esc(a.payloadJson)}</td>
    </tr>
  `).join("");
}

function renderReplayDropdown(publicados) {
  const select = document.getElementById("replaySelect");
  const prev = select.value;
  const options = ['<option value="">Selecione um evento publicado para republicar...</option>']
    .concat(publicados.map(ev =>
      `<option value="${esc(ev.eventId)}">${esc(ev.eventType)} | ${esc(ev.payload?.lote || "")} | ${esc(ev.eventId)}</option>`
    ));
  select.innerHTML = options.join("");
  if (prev && publicados.some(e => e.eventId === prev)) {
    select.value = prev;
  } else if (state.lastPublishedEventId && publicados.some(e => e.eventId === state.lastPublishedEventId)) {
    select.value = state.lastPublishedEventId;
  }
}

async function refreshState() {
  const params = new URLSearchParams();
  const fId = document.getElementById("filterEventId").value.trim();
  const fType = document.getElementById("filterEventType").value.trim();
  const fIni = document.getElementById("filterInicio").value.trim();
  const fFim = document.getElementById("filterFim").value.trim();
  if (fId) params.set("eventId", fId);
  if (fType) params.set("eventType", fType);
  if (fIni) params.set("inicio", fIni);
  if (fFim) params.set("fim", fFim);

  const qs = params.toString();
  try {
    const resp = await fetch("/api/painel/estado" + (qs ? `?${qs}` : ""));
    if (!resp.ok) return;
    const data = await resp.json();

    renderStatusStrip(data.servicos || {});
    renderQueues(data.filas || []);
    renderRecepcao(Boolean(data.servicos?.recepcao), data.ticketsRecepcao || []);
    renderComunicacao(Boolean(data.servicos?.comunicacao), data.avisosComunicacao || []);
    renderQualidade(Boolean(data.servicos?.qualidade), data.analisesQualidade || []);
    renderIndicadores(
      Boolean(data.servicos?.["indicadores-1"]),
      Boolean(data.servicos?.["indicadores-2"]),
      data.indicadoresTotais || [],
      data.indicadoresExecucoes || {}
    );
    renderAuditoria(Boolean(data.servicos?.auditoria), data.auditoriaEventos || []);
    renderReplayDropdown(data.eventosPublicados || []);
  } catch (err) {
    console.error("Erro ao atualizar painel:", err);
  }
}

function configureAutoRefresh() {
  if (state.autoRefreshTimer) {
    clearInterval(state.autoRefreshTimer);
    state.autoRefreshTimer = null;
  }
  if (document.getElementById("autoRefreshToggle").checked) {
    state.autoRefreshTimer = setInterval(refreshState, 2000);
  }
}

document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("[data-endpoint]").forEach(btn => {
    btn.addEventListener("mouseenter", () => updateRoutingPreview(btn.dataset.rk));
    btn.addEventListener("click", () => publishDomainEvent(btn.dataset.endpoint, btn.dataset.rk));
  });

  document.querySelectorAll("[data-preset-rk]").forEach(btn => {
    btn.addEventListener("click", () => {
      document.getElementById("customRoutingKey").value = btn.dataset.presetRk;
      document.getElementById("customEventType").value = btn.dataset.presetType;
      updateRoutingPreview(btn.dataset.presetRk);
    });
  });

  document.getElementById("customPublishBtn").addEventListener("click", publishCustomEvent);
  document.getElementById("batchBtn").addEventListener("click", publishConcurrentBatch);
  document.getElementById("replayBtn").addEventListener("click", republishSelectedEvent);
  document.getElementById("refreshBtn").addEventListener("click", refreshState);
  document.getElementById("autoRefreshToggle").addEventListener("change", configureAutoRefresh);

  document.getElementById("applyAuditFilterBtn").addEventListener("click", refreshState);
  document.getElementById("clearAuditFilterBtn").addEventListener("click", () => {
    ["filterEventId", "filterEventType", "filterInicio", "filterFim"].forEach(id => {
      document.getElementById(id).value = "";
    });
    refreshState();
  });

  refreshState();
  configureAutoRefresh();
});
