# Roteiro de Slides e Guia da Apresentação (20 minutos • 5 integrantes)

> **Objetivo deste documento:** servir como base direta para montar a apresentação (PowerPoint, Google Slides ou Canva) e conduzir os **20 minutos** de exposição + demonstração ao vivo do **Tema 08 — Cooperativa Agrícola com RabbitMQ**, cobrindo todos os critérios de avaliação.

---

## 1. Divisão de Tempo e Participação dos 5 Integrantes

Todos os 5 integrantes devem participar e saber explicar o fluxo. Sugestão de divisão alinhada ao roteiro oficial:

| Bloco do Roteiro | Tempo | Slides | Responsável sugerido | O que apresenta / demonstra |
|---|---:|---|---|---|
| **1. Arquitetura e Contrato** | 3 min | Slides 1–4 | Integrante 1 | Problema da cooperativa, arquitetura, topologia RabbitMQ, justificativa `Topic × Direct × Fanout` e contrato JSON |
| **2. Roteamento (4 Eventos)** | 5 min | Slides 5–6 | Integrante 2 | Matriz Evento × Consumidor, previsão antes de cada clique e comprovação ao vivo no Painel Web (`:8180`) + RabbitMQ UI (`:15672`) |
| **3. Trabalho Concorrente** | 3 min | Slide 7 | Integrante 3 | Duas instâncias de Indicadores (`indicadores-1` e `indicadores-2`), `prefetch=1`, disparo em lote e soma atômica por produto |
| **4. Interrupção e Recuperação** | 4 min | Slide 8 | Integrante 4 | Parada do container `comunicacao`, publicação de evento, retenção em `coop.comunicacao.queue`, processamento pelos demais e retomada |
| **5. Duplicidade e Defesa** | 5 min | Slides 9–12 | Integrante 5 (+ todos nas perguntas) | Republicação do mesmo `eventId`, efeito único no peso, `basicAck` pós-commit e respostas sobre eventos novos / mudança de regra |

---

## 2. Estrutura Slide a Slide (Conteúdo Pronto para Copiar)

### Slide 1 — Capa
- **Título no slide:** Cooperativa Agrícola — Mensageria Orientada a Eventos com RabbitMQ
- **Subtítulo:** Sistemas Distribuídos e Cloud Computing • Tema 08
- **Tópicos do slide:**
  - **Integrantes:**
    - Ryan Tofanini (RA: 269204)
    - João Felipe Lorenzon de Faria (RA: 268756)
    - João Pedro Rodrigues Alves (RA: 269476)
    - Matheus Aparecido Andrade Moraes (RA: 269648)
    - Luis Gustavo Sciarini Marins (RA: 267483)
  - Stack: Java 21 • Spring Boot 4.2 • RabbitMQ (`Topic Exchange`) • PostgreSQL 16 • Docker Compose
- **Fala-guia (15s):** Apresentar o tema e como a equipe dividiu a apresentação entre arquitetura, roteamento, concorrência, resiliência e idempotência.

---

### Slide 2 — Domínio e Desafio da Cooperativa
- **Critério atendido:** Arquitetura (2,0 pts)
- **Tópicos do slide:**
  - **Contexto:** registro de recebimento de cargas e resultados de análise de qualidade por lote.
  - **Produtor (`coop-producer`):** publica fatos do domínio de forma desacoplada.
  - **4 Eventos Obrigatórios:**
    - Processo principal: `Carga recebida` (`carga.recebida`) e `Carga recusada` (`carga.recusada`)
    - Processo especializado: `Análise aprovada` (`analise.aprovada`) e `Análise reprovada` (`analise.reprovada`)
  - **5 Consumidores Independentes:**
    1. **Recepção** → ticket de pesagem (`lote` e `pesoLiquidoKg`)
    2. **Comunicação** → avisos aos cooperados
    3. **Qualidade** → resultados das análises (família `analise.*`)
    4. **Auditoria** → histórico consultável de todos os eventos (`#`)
    5. **Indicadores (2 instâncias)** → soma de peso recebido por produto
- **Visual sugerido:** Diagrama simplificado com 1 produtor à esquerda, RabbitMQ ao centro e os 5 consumidores à direita (exportar do Mermaid em [README.md](../README.md)).
- **Fala-guia (45s):** Destacar que cada consumidor sobe, para e falha de forma independente, sem acoplamento direto com o produtor.

---

### Slide 3 — Topologia RabbitMQ e Justificativa do `Topic Exchange`
- **Critério atendido:** Arquitetura e justificativa do encaminhamento (2,0 pts)
- **Tópicos do slide:**
  - **Exchange principal:** `coop.events.topic` (`type = topic`, `durable = true`)
  - **Dead Letter:** `coop.events.dlx` (`direct`) → 5 filas `*.dlq` para mensagens inválidas
  - **Comparação com duas alternativas estudadas em aula:**

| Critério | **Topic Exchange** *(Escolhida)* | **Direct Exchange** | **Fanout Exchange** |
|---|---|---|---|
| **Seleção por evento** | Sim (`carga.recebida`, `carga.recusada`, `analise.reprovada`) | Sim (igualdade exata) | Não (entrega tudo a todos) |
| **Acompanhamento de família** | Sim (`analise.*` para Qualidade) | Não (exige novo binding a cada fato novo) | Não (mistura eventos de `carga.*`) |
| **Recebimento abrangente** | Sim (`#` para Auditoria) | Não (exige replicar todos os bindings) | Sim (*broadcast* total) |

- **Visual sugerido:** Tabela comparativa acima + lista das 5 filas (`coop.recepcao.queue`, `coop.comunicacao.queue`, `coop.qualidade.queue`, `coop.auditoria.queue`, `coop.indicadores.queue`).
- **Fala-guia (60s):** Explicar que somente a **Topic Exchange** atende simultaneamente seleção exata, assinatura de família (`*`) e captura global (`#`) sem desperdiçar mensagens nem acoplar novos eventos a reconfigurações manuais.

---

### Slide 4 — Contrato JSON e Fluxo de Publicação
- **Critério atendido:** Arquitetura e Contrato (2,0 pts)
- **Tópicos do slide:**
  - Contrato único `CooperativeEvent`: identificador único, tipo, chave de roteamento, horário UTC e payload do domínio.
  - **Reprodutibilidade:** `eventId` explícito permite repetir exatamente a mesma publicação no teste de duplicidade.
  - **Garantia na publicação:** `MessageDeliveryMode.PERSISTENT` + `Publisher Confirms` (`correlated`) + `mandatory = true`.
- **Visual sugerido (colar o JSON no slide):**
  ```json
  {
    "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "eventType": "CARGA_RECEBIDA",
    "routingKey": "carga.recebida",
    "occurredAt": "2026-11-24T13:30:00Z",
    "payload": {
      "lote": "LOTE-2026-001",
      "cooperado": "Fazenda Boa Vista",
      "produto": "SOJA",
      "pesoLiquidoKg": 18500.50,
      "motivo": null
    }
  }
  ```
- **Fala-guia (45s):** Explicar o caminho completo: HTTP `POST /api/eventos/...` → grava em `published_events` → publica em `coop.events.topic` com persistência → broker confirma ACK → roteia para as filas compatíveis → consumidores gravam no PostgreSQL antes de confirmar `basicAck`.

---

### Slide 5 — Matriz Evento × Consumidor (Previsão de Roteamento)
- **Critério atendido:** Roteamento dos quatro eventos e evidências (2,0 pts)
- **Visual principal do slide (tabela grande e legível):**

| Evento (`routingKey`) | 1. Recepção (`carga.recebida`) | 2. Comunicação (`carga.recebida`, `carga.recusada`, `analise.reprovada`) | 3. Qualidade (`analise.*`) | 4. Auditoria (`#`) | 5. Indicadores (`carga.recebida`) |
|---|:---:|:---:|:---:|:---:|:---:|
| **Carga recebida** (`carga.recebida`) | **SIM** | **SIM** | NÃO | **SIM** | **SIM** |
| **Carga recusada** (`carga.recusada`) | NÃO | **SIM** | NÃO | **SIM** | NÃO |
| **Análise aprovada** (`analise.aprovada`) | NÃO | NÃO | **SIM** | **SIM** | NÃO |
| **Análise reprovada** (`analise.reprovada`) | NÃO | **SIM** | **SIM** | **SIM** | NÃO |

- **Fala-guia:** Apresentar a regra de negócio da matriz e anunciar que agora a equipe vai **prever antes de clicar** e conferir os efeitos na prática.

---

### Slide 6 — Guia da Demo 1: Roteamento ao Vivo (5 min)
*(Este slide serve de guia visual durante a troca de tela para o Painel Web e RabbitMQ UI)*
- **Roteiro na tela (`http://localhost:8180` + `http://localhost:15672`):**
  1. Passar o mouse / selecionar **Carga Recebida**: anunciar *"Recebem: Recepção, Comunicação, Auditoria e Indicadores. NÃO recebe: Qualidade."* → Clicar e conferir os 4 cards + Auditoria.
  2. Selecionar **Carga Recusada**: anunciar *"Recebem: Comunicação e Auditoria. NÃO recebem: Recepção, Qualidade e Indicadores."* → Clicar e conferir aviso `RECUSA_CARGA`.
  3. Selecionar **Análise Aprovada**: anunciar *"Recebem: Qualidade e Auditoria. NÃO recebem: Recepção, Comunicação e Indicadores."* → Clicar e conferir laudo `APROVADA`.
  4. Selecionar **Análise Reprovada**: anunciar *"Recebem: Comunicação, Qualidade e Auditoria. NÃO recebem: Recepção e Indicadores."* → Clicar e conferir aviso `REPROVACAO_ANALISE` e laudo `REPROVADA`.
  5. Mostrar no card **4. Auditoria** os filtros por `eventId`, `eventType` e intervalo de horário (`inicio`/`fim`).

---

### Slide 7 — Trabalho Concorrente: Duas Instâncias do 5º Consumidor (3 min)
- **Critério atendido:** Distribuição de trabalho entre instâncias (1,5 pt)
- **Tópicos do slide:**
  - **Padrão utilizado:** *Competing Consumers* na mesma fila (`coop.indicadores.queue`).
  - **Instâncias ativas:** `indicadores-1` (`:8185`) e `indicadores-2` (`:8186`).
  - **Configuração chave:** `spring.rabbitmq.listener.simple.prefetch=1` → evita que uma instância drene mensagens em rajada e garante divisão equilibrada.
  - **Consistência sob concorrência:** soma atômica no PostgreSQL (`INSERT ... ON CONFLICT (produto) DO UPDATE`) evita *lost updates* quando ambas somam o mesmo produto simultaneamente.
- **Ação ao vivo na tela:**
  - Clicar em **"Disparar Lote Concorrente (6 cargas)"** no Painel Web (`http://localhost:8180`).
  - Mostrar o contador `indicadores-1: X` vs `indicadores-2: Y` e a tabela de execuções (`PROCESSADO`), além dos cards de Recepção, Comunicação e Auditoria também atualizados.

---

### Slide 8 — Interrupção e Recuperação de Consumidor (4 min)
- **Critério atendido:** Interrupção e recuperação do consumidor (1,5 pt)
- **Tópicos do slide:**
  - **Por que mensagens não se perdem?**
    - Fila `coop.comunicacao.queue` é **durável** (`durable = true`) e já declarada na topologia.
    - Mensagens publicadas com `delivery_mode = 2` (`PERSISTENT`).
    - Consumidores independentes possuem filas próprias (isolamento de falhas).
- **Ação ao vivo na tela:**
  1. Parar **apenas** `coop-consumer-comunicacao` (no **Docker Desktop** clicar *Stop*, ou executar `docker compose stop comunicacao`).
  2. Mostrar no Painel Web o badge `OFFLINE` em Comunicação e `Consumidores: 0` na fila `coop.comunicacao.queue`.
  3. Publicar **Análise Reprovada** (ou **Carga Recebida**).
  4. Comprovar que **Qualidade** e **Auditoria** processaram na hora, enquanto `coop.comunicacao.queue` retém `1` mensagem pendente (`Ready = 1`) — confirmar no **RabbitMQ UI (:15672)** também.
  5. Religar `comunicacao` (clicar *Start* no Docker Desktop ou `docker compose start comunicacao`), aguardar voltar a `UP`, mostrar a fila zerando (`Ready = 0`) e o aviso aparecendo no card de **Comunicação**.

---

### Slide 9 — Confirmação de Consumo (`ACK`) e Confiabilidade
- **Critério atendido:** Tratamento de duplicidade e explicação da confirmação (2,0 pts)
- **Tópicos do slide:**
  - **Modo adotado:** `acknowledge-mode: manual` (`ReliableEventConsumer`).
  - **Quando a entrega é confirmada?**
    1. Mensagem chega no `@RabbitListener`.
    2. `TransactionTemplate` abre transação no PostgreSQL.
    3. Grava reivindicação em `processed_events` + efeito de negócio.
    4. Banco executa **`COMMIT`**.
    5. **Somente após o `COMMIT`** o consumidor envia `channel.basicAck(deliveryTag, false)`.
  - **Em caso de erro:**
    - Erro transitório de banco (`DataAccessException`) → *rollback* + `basicNack(requeue=true)` para reprocessar depois.
    - Mensagem inválida → *rollback* + `basicNack(requeue=false)` → enviada para `coop.events.dlx` → `*.dlq`.
- **Visual sugerido:** Diagrama linear em 5 passos: `Mensagem → Transação DB → COMMIT → basicAck` (com seta alternativa de erro para `NACK / DLQ`).

---

### Slide 10 — Tratamento de Duplicidade (Idempotência Distribuída)
- **Critério atendido:** Tratamento de duplicidade (2,0 pts)
- **Tópicos do slide:**
  - **Cenário crítico:** se a duplicata de um `eventId` já processado por `indicadores-1` cair em `indicadores-2`, memória local (`HashSet`) falharia!
  - **Solução adotada:** tabela compartilhada `processed_events` com chave primária `(consumer_name, event_id)`:
    ```sql
    INSERT INTO processed_events (consumer_name, event_id, instance_id)
    VALUES ('indicadores', :eventId, :instanceId)
    ON CONFLICT (consumer_name, event_id) DO NOTHING;
    ```
  - Primeira entrega retorna `1` linha afetada → soma o peso em `indicadores_totais` e registra `PROCESSADO`.
  - Republicação retorna `0` linhas afetadas → **não soma o peso**, registra `DUPLICADO_IGNORADO` em `indicadores_execucoes` e envia `basicAck`.
- **Ação ao vivo na tela:**
  - Selecionar no dropdown um evento `CARGA_RECEBIDA` já processado (anotar o peso atual do produto, ex.: `SOJA`), clicar em **"Republicar Mesmo eventId (Duplicidade)"** 1 ou 2 vezes.
  - Mostrar que o total em **Indicadores** permaneceu idêntico (peso somado uma única vez) e apareceu `DUPLICADO_IGNORADO` no histórico de execuções.

---

### Slide 11 — Eventos Novos e Mudança de Regra (Defesa)
- **Critério atendido:** Arquitetura + Domínio na apresentação (2,0 + 1,0 pts)
- **Tópicos do slide:**
  - **Caso 1 — Evento novo em família já acompanhada (`analise.reavaliada`):**
    - Roteado automaticamente para `coop.qualidade.queue` (`analise.*`) e `coop.auditoria.queue` (`#`), **sem alterar código ou bindings**.
    - `QualidadeService` usa `eventType` como `String` extensível (não `enum` fechado), registrando normalmente.
  - **Caso 2 — Evento novo sem regra específica (`faturamento.emitido`):**
    - Ignorado pelos 4 consumidores de negócio (`Recepção`, `Comunicação`, `Qualidade`, `Indicadores`).
    - Capturado apenas pela **Auditoria** (`#`). Se não houvesse fila `#`, a Topic Exchange descartaria a mensagem sem rota (e o produtor receberia retorno via `mandatory=true` + `ReturnsCallback`).
- **Ação ao vivo (se solicitado pela banca):** usar os atalhos no Painel Web (`analise.reavaliada` e `faturamento.emitido`) e mostrar o efeito imediato em Qualidade/Auditoria.

---

### Slide 12 — Reprodutibilidade e Encerramento
- **Critério atendido:** Reprodutibilidade da entrega (1,0 pt)
- **Tópicos do slide:**
  - **Repositório completo:** código multi-módulo Maven (`coop-common`, `coop-producer`, 5 consumidores).
  - **Broker reproduzível:** `compose.yaml` + `rabbitmq/rabbitmq.conf` + `rabbitmq/definitions.json`.
  - **Execução em 1 comando:** `docker compose up -d --build --wait` (ou scripts locais `scripts/start-all.ps1`).
  - **Verificação automatizada:** `.\mvnw.cmd test` (19 testes unitários) e `.\scripts\verify-e2e.ps1 -Mode Docker`.
  - **Principais decisões técnicas:** `Topic Exchange`, ACK manual pós-commit, idempotência atômica no PostgreSQL e *competing consumers* com `prefetch=1`.

---

## 3. Cola Rápida de Respostas para Perguntas da Banca

1. **"Por que vocês usaram `Topic Exchange` e não `Direct` ou `Fanout`?"**
   - `Direct` exige igualdade exata e quebraria o requisito de `Qualidade` receber novos eventos da família `analise.*` sem alterar bindings; `Fanout` entrega tudo para todas as filas e impediria seleção por evento (`Recepção` receberia recusas e análises). A `Topic` resolve seleção exata (`carga.recebida`), família (`analise.*`) e captura total (`#`) em uma única exchange.
2. **"E se um evento da família tiver 3 níveis, como `analise.micotoxina.reprovada`?"**
   - Na convenção atual `<processo>.<fato>`, usamos `analise.*` (1 palavra após o ponto). Se o domínio adotar subníveis, basta alterar o binding de `Qualidade` para `analise.#` (0 ou mais palavras), mantendo isolamento de `carga.*`.
3. **"E se uma regra mudar (ex.: `Comunicação` também precisar avisar sobre `analise.aprovada`)?"**
   - Como a topologia é declarativa, basta adicionar o binding `analise.aprovada` na fila `coop.comunicacao.queue` (em `definitions.json` e `CooperativeRabbitConfig`) e mapear o texto de aviso correspondente no consumidor, sem alterar o produtor nem os demais consumidores.
4. **"Quando a mensagem é removida da fila no RabbitMQ?"**
   - Apenas quando o consumidor chama `channel.basicAck(deliveryTag, false)`, o que ocorre **somente após o `COMMIT` da transação no PostgreSQL**. Se o serviço cair antes do commit, o banco faz rollback e o RabbitMQ reentrega a mensagem.
5. **"Por que usar PostgreSQL para deduplicação em vez de uma lista em memória?"**
   - Porque o consumidor 5 tem **duas instâncias** (`indicadores-1` e `indicadores-2`) e os processos podem ser reiniciados. Com `processed_events` no PostgreSQL (`PRIMARY KEY (consumer_name, event_id)`), mesmo que a 1ª entrega caia na instância 1 e a duplicata caia na instância 2, a soma ocorre uma única vez.
6. **"Como vocês garantiram que as duas instâncias de Indicadores não ficassem desbalanceadas ou somando errado?"**
   - Configuramos `prefetch = 1` para o broker distribuir uma mensagem por vez entre as duas conexões, e usamos `INSERT ... ON CONFLICT (produto) DO UPDATE` para incrementar o total de forma atômica no banco sem *lost update*.

---

## 4. Checklist Pré-Apresentação (5 minutos antes de entrar)

- [ ] Docker Desktop aberto com os 9 containers `healthy` (`docker compose ps`).
- [ ] Limpar dados antigos de teste se quiser começar zerado (opcional: rodar `.\scripts\verify-e2e.ps1 -Mode Docker -KeepRunning` ou truncar tabelas).
- [ ] Abas abertas no navegador:
  - Aba 1: Painel Web — `http://localhost:8180`
  - Aba 2: RabbitMQ Management UI — `http://localhost:15672` (credenciais locais definidas no `.env`), na aba **Queues**.
- [ ] Janela do Docker Desktop (ou terminal com `docker compose stop comunicacao` / `docker compose start comunicacao`) pronta para a Etapa 4.
- [x] Nomes dos 5 integrantes preenchidos no Slide 1 e no `README.md`.
