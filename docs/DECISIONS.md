# Registro de Decisões de Arquitetura (ADR)

## D-01 — Linguagem e Ecossistema
- **Data:** 30/08/2026
- **Decisão:** Kotlin como linguagem principal da nossa implementação, consumindo os contratos e testes originais que estão em Java.
- **Alternativas consideradas:** Usar 100% Java ou 100% Kotlin traduzindo o repositório base.
- **Motivo:** O Android atual é otimizado para Kotlin. O invólucro do motor local de IA e o Jetpack Compose fluem muito melhor com Kotlin.
- **Consequências:** Atenção redobrada com anotações de nulabilidade e `@JvmStatic` na hora de interligar as lógicas em Kotlin com os testes originais do professor escritos em Java.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-02 — Concorrência e Tempo Real
- **Data:** 30/08/2026
- **Decisão:** Uso de `Thread` e `ExecutorService` na Camada 1 (Aquisição) e *Corrotinas* na Camada 3 (Agente).
- **Alternativas consideradas:** Corrotinas em tudo; Threads em tudo.
- **Motivo:** A Camada 1 exige um-para-um (tarefa/thread) visível para o cálculo de Rate Monotonic cobrado na tabela de escalonabilidade da Fase II. A Camada 3 ganha com o cancelamento estruturado e o `withTimeout` do Kotlin.
- **Consequências:** O relatório da AV2 terá que mapear explicitamente a Thread alocada na Camada 1 e indicar qual despachante as corrotinas usam na Camada 3 para análise de prioridades.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-03 — Persistência dos Dados Sensíveis
- **Data:** 30/08/2026
- **Decisão:** `SQLiteOpenHelper` com os campos sensíveis cifrados individualmente.
- **Alternativas consideradas:** Room (ORM) ou Arquivo anexo simples.
- **Motivo:** Evitar verbosidade de bibliotecas gigantes. Permite ver exatamente a *query* gerada e atende bem ao requisito de cifrar apenas colunas específicas via `AesGcmVault` implementado por nós, sem delegar a segurança para o ORM (que tira os pontos da AV2).
- **Consequências:** O SQL não terá verificação de tipagem em tempo de compilação, exigindo cuidado para não cometer erros de digitação em nomes de colunas.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-04 — Divisão de Trabalho do Trio
- **Data:** 30/08/2026
- **Decisão:** Sistema de Rodízio Semanal (Codificador Primário, Secundário, e Revisor/Testador).
- **Alternativas consideradas:** Divisão por pacote/arquivo permanente.
- **Motivo:** A rubrica de contribuição exige que todo membro tenha *commits* rastreáveis em todas as semanas, avaliando o domínio completo na arguição. O modelo de divisão por arquivo faz com que um integrante não saiba explicar o código do outro na banca.
- **Consequências:** Exigirá forte disciplina no uso de ramos (*branches*) e *Pull Requests* para que todos compreendam 100% da lógica e tenham pontuação máxima.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-05 — Nível de API Alvo
- **Data:** 30/08/2026
- **Decisão:** `minSdk 26` (Android 8.0).
- **Alternativas consideradas:** `minSdk 24` ou `minSdk 29+`.
- **Motivo:** O Android 8.0 (API 26) garante suporte à leitura das acurácias da velocidade via sensor GPS (GNSS), que é o dado basilar usado pela matriz de covariância na reconciliação (Camada 2).
- **Consequências:** Aparelhos muito antigos serão excluídos, mas todos os integrantes do grupo possuem hardware igual ou superior à API 26.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## Registro de implementação S2 — 22/09/2026

As decisões abaixo concretizam a prioridade solicitada por Juliano nesta entrega.
Devem ser revisadas pelos parceiros no PR, conforme D-04; não representam aprovação
do docente ou revisão já realizada pelo trio. As decisões D-01 a D-05 continuam válidas.

### D-06 — Comunicação direta antes da detecção automática
- **Decisão:** prova Wi-Fi Direct com canal TCP bidirecional na porta 8988 e SOS manual;
  uma conexão entre dois aparelhos por sessão, sem servidor externo ou internet.
- **Motivo:** testar o risco de descoberta/conexão nos aparelhos reais, isoladamente
  da aquisição, inferência e detecção de quedas.
- **Consequências:** interface `Transporte` separa descoberta/conexão do protocolo.
  Mensagens v1 carregam UUID, origem, tipo, limite de saltos e referência/destino do ACK.
  O ACK confirma o recebimento pelo aplicativo. Deduplicação limitada a 256 IDs,
  saída limitada a 64 mensagens e no máximo 16 SOS aguardando confirmação por 10 s.
  Retransmissão, autenticação entre nós, entrega durável e malha multi-hop não estão
  implementadas. O teste TCP local não substitui o ensaio físico Wi-Fi Direct.

### D-07 — Incerteza com origem explícita
- **Decisão:** converter raio GNSS de 68% em variância por eixo sob hipótese gaussiana
  isotrópica; velocidade escalar usa aproximação sigma = acurácia reportada. Matriz em
  metros e m/s, não em graus. Correlações assumidas nulas são uma hipótese do modelo.
- **Consequências:** ausência de acurácia/velocidade gera resultado indisponível.
  Qualidade IMU é categórica, nunca convertida em sigma. `IncertezaImu.Reportada` só
  admite fonte quantitativa identificada; o adaptador Android usa `Indisponivel`.
  Covariância IMU por calibração precisa de aceite do docente. Não foi implementada
  uma calibração nem declarado cumprimento integral desse item.

### D-08 — Buffer, aquisição e tempo
- **Decisão:** `CircularImuBuffer` de capacidade fixa, inserção O(1), mesmo monitor
  para índices, conteúdo e contadores; descartar o mais antigo ao lotar e contar perdas.
  Snapshot e drenagem retornam coleções independentes com leituras imutáveis.
- **Consequências:** aquisição em `HandlerThread`, período solicitado de 20.000 µs
  (50 Hz); consumidor em `ScheduledExecutorService` drena fora da thread de aquisição.
  Futuras persistência e IA receberão o mesmo lote do consumidor; não disputarão
  remoções na fila. S2 só apresenta métricas, sem escrever leituras sensíveis em disco.
  `LeituraImu` mantém timestamp monotônico em ns e aceleração bruta com gravidade,
  sem inventar atitude. `IMURead` e `Trajeto` da S1 preservam seus contratos.
  Não há alteração da decisão de corrotinas para o futuro agente.

### D-09 — Escopo Android e projeto de referência
- **Decisão:** usar o projeto da raiz e seu módulo `app/src`; a cópia `app/app` não
  recebe a S2 e não deve ser aberta como projeto principal. Nenhum arquivo histórico
  dessa cópia foi excluído nesta entrega.
- **Consequências:** interface XML/AppCompat existente é mantida. Serviço de primeiro
  plano continua futuro: sair da tela encerra sensores, GNSS e conexão. Permissões
  são pedidas no uso; Android 17 inclui `ACCESS_LOCAL_NETWORK` para os sockets.
  Backup foi desativado para não replicar a identidade do nó. SQLiteOpenHelper,
  AES-GCM/Keystore de D-03 continuam previstos, sem persistência sensível nesta prova.
  Toolchain existente: daemon JDK 25, bytecode Java 11, minSdk 26, SDK 37.
