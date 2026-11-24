CREATE TABLE IF NOT EXISTS published_events (
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    routing_key VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS processed_events (
    consumer_name VARCHAR(60) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    instance_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE IF NOT EXISTS recepcao_tickets (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    lote VARCHAR(80) NOT NULL,
    produto VARCHAR(80) NOT NULL,
    cooperado VARCHAR(150),
    peso_liquido_kg NUMERIC(14, 3) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS comunicacao_avisos (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    tipo_aviso VARCHAR(60) NOT NULL,
    lote VARCHAR(80) NOT NULL,
    cooperado VARCHAR(150) NOT NULL,
    produto VARCHAR(80),
    peso_liquido_kg NUMERIC(14, 3),
    motivo TEXT,
    mensagem TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS qualidade_resultados (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    routing_key VARCHAR(120) NOT NULL,
    lote VARCHAR(80) NOT NULL,
    produto VARCHAR(80),
    resultado VARCHAR(80) NOT NULL,
    motivo TEXT,
    detalhes_json TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS auditoria_eventos (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    routing_key VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL,
    delivery_count INTEGER NOT NULL DEFAULT 1,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_auditoria_event_type ON auditoria_eventos (event_type);
CREATE INDEX IF NOT EXISTS idx_auditoria_occurred_at ON auditoria_eventos (occurred_at);

CREATE TABLE IF NOT EXISTS indicadores_totais (
    produto VARCHAR(80) PRIMARY KEY,
    peso_total_kg NUMERIC(16, 3) NOT NULL DEFAULT 0,
    total_cargas BIGINT NOT NULL DEFAULT 0,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS indicadores_execucoes (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    instance_id VARCHAR(100) NOT NULL,
    lote VARCHAR(80) NOT NULL,
    produto VARCHAR(80) NOT NULL,
    peso_liquido_kg NUMERIC(14, 3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
