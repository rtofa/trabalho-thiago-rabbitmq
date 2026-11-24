# Arquitetura, Decisões Técnicas e Justificativa do Encaminhamento

## 1. Desenho da Topologia no RabbitMQ

- **Exchange principal:** `coop.events.topic` (`type = topic`, `durable = true`, `auto_delete = false`)
- **Dead Letter Exchange (DLX):** `coop.events.dlx` (`type = direct`, `durable = true`)
- **Persistência das mensagens:** o produtor publica com `MessageDeliveryMode.PERSISTENT` (`delivery_mode = 2`), **Publisher Confirms** (`correlated`) e `mandatory = true`.

### Filas, Vínculos (*Bindings*) e Critérios de Encaminhamento

| Consumidor | Fila Durável | DLQ | Binding Key(s) | Critério |
|---|---|---|---|---|
| 1. Recepção | `coop.recepcao.queue` | `coop.recepcao.dlq` | `carga.recebida` | Seleção exata de evento |
| 2. Comunicação com cooperados | `coop.comunicacao.queue` | `coop.comunicacao.dlq` | `carga.recebida`, `carga.recusada`, `analise.reprovada` | Múltiplas chaves exatas específicas |
| 3. Qualidade | `coop.qualidade.queue` | `coop.qualidade.dlq` | `analise.*` | Acompanhamento de família de eventos |
| 4. Auditoria | `coop.auditoria.queue` | `coop.auditoria.dlq` | `#` | Recebimento abrangente (todos os eventos) |
| 5. Indicadores de recepção | `coop.indicadores.queue` | `coop.indicadores.dlq` | `carga.recebida` | Seleção exata + *Competing Consumers* (2 instâncias, `prefetch=1`) |

A convenção adotada para chaves de roteamento é `<processo>.<fato>` (dois tokens separados por ponto):
- Processo principal de carga: `carga.recebida`, `carga.recusada`
- Processo especializado de qualidade: `analise.aprovada`, `analise.reprovada` (e futuros fatos como `analise.reavaliada`)

---

## 2. Comparação do Exchange Escolhido com Duas Alternativas Estudadas em Aula

Para atender simultaneamente às cinco responsabilidades da cooperativa, comparou-se a **Topic Exchange** (escolhida) com **Direct Exchange** e **Fanout Exchange** nos três critérios exigidos:

| Critério | **Topic Exchange** *(Escolhida)* | **Direct Exchange** *(Alternativa 1)* | **Fanout Exchange** *(Alternativa 2)* |
|---|---|---|---|
| **Seleção por evento** | **Atende nativamente** com chave literal sem curingas (`carga.recebida`, `carga.recusada`, `analise.reprovada`). | **Atende nativamente** por igualdade exata da *routing key*. | **Não atende.** Ignora a *routing key* e envia todas as mensagens para todas as filas, exigindo descarte manual na aplicação. |
| **Acompanhamento de uma família** | **Atende nativamente** via curinga `analise.*` (ou `analise.#` para subníveis), capturando automaticamente novos eventos da família sem alterar código nem bindings. | **Não atende nativamente.** Exigiria cadastrar manualmente um novo binding para cada novo tipo de evento criado na família de qualidade, gerando acoplamento operacional e risco de perda de eventos novos. | **Não atende.** O consumidor de Qualidade receberia também eventos de `carga.*`, desperdiçando rede/CPU e violando o isolamento de roteamento. |
| **Recebimento abrangente** | **Atende nativamente** via curinga `#` na fila de Auditoria (`coop.auditoria.queue`), capturando todos os eventos de todas as famílias. | **Não atende nativamente.** Teria de manter uma cópia exata de todos os bindings existentes e futuros na fila de Auditoria. | **Atende nativamente** (é o caso de uso típico de *broadcast* integral), mas ao custo de inviabilizar os dois critérios anteriores na mesma exchange. |

### Conclusão da Justificativa
A **Topic Exchange** é a única dentre as três que resolve os três padrões em uma única exchange sem lógica de filtro espúria nos consumidores e sem exigir reconfiguração de bindings quando novos eventos surgem na família de qualidade ou na auditoria.

---

## 3. Comportamento diante de Eventos Novos

### 3.1. Evento novo pertencente a uma família já acompanhada
Exemplo: publicação de `ANALISE_REAVALIADA` com `routingKey = "analise.reavaliada"`.
- O padrão `analise.*` ligado a `coop.qualidade.queue` casa exatamente com qualquer chave `<analise>.<um-termo>`.
- O padrão `#` ligado a `coop.auditoria.queue` casa com qualquer chave.
- Resultado: **Qualidade** e **Auditoria** recebem e registram o novo evento imediatamente sem nenhuma alteração no broker ou deploy dos consumidores. Além disso, o serviço `QualidadeService` trata `eventType` como string extensível (e não um `enum` fechado), evitando falhas de desserialização para fatos novos da família.
- **Recepção**, **Comunicação** e **Indicadores** permanecem isolados e não recebem o evento, pois não possuem binding correspondente.

> *Observação:* Caso futuros eventos da família utilizem mais de dois níveis hierárquicos (ex.: `analise.micotoxina.reprovada`), basta substituir o binding `analise.*` por `analise.#`.

### 3.2. Evento novo que não atende a nenhuma regra específica de um consumidor
Exemplo: publicação de `FATURAMENTO_EMITIDO` com `routingKey = "faturamento.emitido"`.
- Nenhuma das regras específicas dos consumidores de negócio (`carga.recebida`, `carga.recusada`, `analise.reprovada`, `analise.*`) casa com `faturamento.emitido`.
- No cenário completo da cooperativa, apenas **Auditoria** (`#`) recebe o evento e o preserva para rastreabilidade.
- Caso não existisse uma fila abrangente (`#`) ligada à `Topic Exchange`, o comportamento padrão do AMQP seria o **descarte silencioso** da mensagem sem rota no broker; como configuramos `mandatory = true` e `ReturnsCallback` no produtor (`RabbitProducerConfig`), qualquer mensagem sem nenhuma fila correspondente seria devolvida ao produtor e registrada em log (`basic.return`), podendo ainda ser combinada com uma `alternate-exchange` em produção.

---

## 4. Confirmação de Consumo (*Acknowledge*) e Tratamento de Duplicidade

### 4.1. Quando a entrega é confirmada
Todos os cinco consumidores operam com `acknowledge-mode: manual` e utilizam o componente compartilhado `ReliableEventConsumer`:
1. O listener recebe a mensagem e delega o processamento ao método transacional via `TransactionTemplate`.
2. Dentro dessa transação no PostgreSQL, o consumidor tenta registrar o processamento inédito em `processed_events` e persiste o efeito de domínio (ticket, aviso, análise, auditoria ou soma de peso).
3. **Somente após o `COMMIT` da transação no banco**, o consumidor executa `channel.basicAck(deliveryTag, false)`.
4. Em caso de falha transitória de banco (`DataAccessException`), a transação sofre `ROLLBACK` e o consumidor envia `basicNack(deliveryTag, false, true)` para reentrega segura.
5. Em caso de mensagem inválida ou falha permanente, o consumidor envia `basicNack(deliveryTag, false, false)`, encaminhando a mensagem para a **Dead Letter Queue** (`*.dlq` via `coop.events.dlx`) para evitar bloqueio infinito da fila.

### 4.2. Como a duplicidade é tratada (Efeito Único entre Múltiplas Instâncias)
- Mesmo com `basicAck` manual, entregas duplicadas podem ocorrer por republicação intencional (mesmo `eventId`) ou por queda do consumidor após o commit no banco e antes do envio do ACK pela rede (*at-least-once delivery*).
- Para garantir **efeito único mesmo com duas instâncias concorrentes do 5º consumidor (`indicadores-1` e `indicadores-2`)**, o controle de idempotência não fica em memória local, e sim na tabela compartilhada `processed_events` com chave primária composta `(consumer_name, event_id)`:
  ```sql
  INSERT INTO processed_events (consumer_name, event_id, instance_id)
  VALUES ('indicadores', :eventId, :instanceId)
  ON CONFLICT (consumer_name, event_id) DO NOTHING;
  ```
- Na primeira entrega, o `INSERT` retorna `1` linha afetada e executa o incremento atômico de peso em `indicadores_totais` (`INSERT ... ON CONFLICT (produto) DO UPDATE SET peso_total_kg = peso_total_kg + EXCLUDED.peso_total_kg`).
- Na republicação do mesmo `eventId` — ainda que o RabbitMQ entregue a segunda cópia para a **outra** instância (`indicadores-2` em vez de `indicadores-1`) — o `INSERT` retorna `0` linhas afetadas, o peso **não** é somado novamente, a tentativa duplicada é auditada em `indicadores_execucoes` com status `DUPLICADO_IGNORADO` e o consumidor confirma `basicAck` para limpar a fila.
