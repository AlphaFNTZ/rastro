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

## Registro de implementação S3 — 24/09/2026

### D-10 — Nearby Connections como enlace
- **Decisão:** substituir Wi-Fi Direct/TCP por Nearby Connections 19.3.0, estratégia
  `P2P_CLUSTER`, com advertising e discovery simultâneos. O `serviceId` é o pacote
  do app e o UUID persistente do nó é publicado como nome do endpoint.
- **Motivo:** eliminar seleção manual de rede/aparelho e permitir múltiplos vizinhos.
- **Consequências:** aparelhos precisam de Google Play Services. A versão 19.5.0
  proposta inicialmente exige metadados Kotlin 2.4, incompatíveis com o AGP 9.3.3
  adotado no projeto; 19.3.0 é o recuo compatível validado. Nearby é apenas enlace:
  não encaminha mensagens para nós fora do alcance. Nesta versão, as permissões
  `ACCESS_WIFI_STATE` e `CHANGE_WIFI_STATE` não podem ser limitadas à API 31: o SDK
  ainda as verifica em versões posteriores e retorna o erro 8032 quando ausentes.

### D-11 — Roteamento inicial por flooding
- **Decisão:** `MeshRouter` aplica UUID, cache limitado, TTL e retransmissão a todos
  os vizinhos exceto o enlace de entrada. ACKs destinados à origem usam o mesmo
  flooding controlado.
- **Motivo:** obter uma prova A → B → C simples antes de introduzir tabelas de rota.
- **Consequências:** o custo cresce com a densidade da rede. Cada aparelho que recebe
  um SOS gera ACK; métricas futuras devem distinguir primeiro ACK e destino desejado.

### D-12 — Serviço de primeiro plano e confiança temporária
- **Decisão:** rede, IMU e GNSS pertencem a `RastroService`; a Activity apenas solicita
  permissões, inicia explicitamente o serviço e observa seu estado. O serviço usa os
  tipos `connectedDevice|location` e notificação persistente.
- **Decisão de segurança provisória:** aceitar automaticamente conexões Nearby sem
  validar o token, somente durante o protótipo acadêmico.
- **Consequências:** fechar a tela não encerra aquisição/rede. Android ainda exige
  ação inicial, permissões e notificação. Qualquer aparelho com o mesmo `serviceId`
  pode entrar na malha; antes de dados reais é obrigatório pareamento autenticado.

### D-13 — Identidade técnica separada do nome visual
- **Decisão:** cada anúncio usa `rastro1|UUID|nome-base64url`. O UUID persistente
  governa conexão/deduplicação; o nome de até 32 bytes é editável e salvo localmente.
  A interface acrescenta os oito primeiros caracteres do UUID ao nome e mostra os
  estados descoberto, conectando e conectado.
- **Motivo:** permitir identificação humana sem usar nomes potencialmente duplicados
  como identidade de protocolo.
- **Consequências:** mudar o nome reinicia advertising/discovery e refaz os enlaces.
  Nome e UUID anunciados ainda não são autenticados e não devem indicar confiança.

### D-14 — Telemetria de enlace e presença multi-hop
- **Decisão:** enlaces locais exibem a qualidade informada por
  `onBandwidthChanged`; o transporte é rotulado como **Nearby automático**, pois o
  SDK não revela se a conexão corrente usa Bluetooth clássico, BLE ou Wi-Fi. O grupo
  Android **Dispositivos próximos** é tratado como permissão, não como transporte.
  Mensagens `PRESENCA` no protocolo v2 são inundadas a cada cinco segundos e carregam
  origem, nome e contagem de saltos. Rotas não renovadas expiram em 16 segundos.
- **Motivo:** distinguir com honestidade nós diretos e indiretos, sem atribuir ao SDK
  uma observabilidade de rádio que ele não oferece.
- **Consequências:** a qualidade só existe para enlaces diretos. A rota indireta é o
  primeiro caminho observado para cada anúncio, não uma garantia de menor caminho;
  mudanças podem levar até a expiração para desaparecer da interface. O protocolo
  mantém leitura das mensagens v1 para compatibilidade durante a atualização.

## Melhorias 01 — 25/09/2026

### D-15 — SOS v3 e estado da sessão

- **Decisão:** SOS imutável com nome, hora UTC, posição opcional, precisão opcional,
  idade monotônica e condição da posição na emissão. Protocolo limitado a 1024 bytes
  com leitura v1/v2; todos os aparelhos precisam ser atualizados para transmitir v3.
- **Aquisição:** serviço inicia GPS/rede com o monitoramento, intervalo de 5 s e
  posição recente até 60 s por padrão (configurável). `PosicaoMapper` não exige
  velocidade e não modifica o contrato científico de `LocationMapper`.
- **Histórico:** 100 eventos em memória; último SOS pela primeira entrega local,
  não pelo relógio remoto. ACK/timeout usam relógio monotônico local. A identidade
  declarada permanece não autenticada. Persistência sensível segue futura em D-03.

### D-16 — MapLibre, região offline e perfis medidos

- **Decisão:** MapLibre Native Android 13.6.1 em XML, estilo Bright do OpenFreeMap,
  atribuição OpenMapTiles/OpenStreetMap. Uma região preparada pelo usuário, até
  20 km por lado e zoom 16, orçamento de 256 MiB de recursos + 32 MiB de cache.
  O orçamento é verificado em progresso; recursos em trânsito e SQLite podem
  ultrapassá-lo. Nenhum download em massa do servidor raster público OSM.
- **Resiliência:** mapa-base ausente não remove coordenadas nem os marcadores.
  O mapa não inicia aquisição paralela e respeita câmera explorada pelo usuário.
- **Alcance:** nenhuma conversão de qualidade Nearby para metros. Perfis JSON
  importados registram aparelhos, condições, evidência, distâncias e ACK/falhas.
  Raio exige ao menos dez repetições por distância e 90% de sucesso no prazo do
  ensaio. Camada tracejada, inicialmente desligada, com confirmação de condições
  para cada novo SOS. Não há medições físicas embutidas ou cobertura garantida.
- **Operação:** veja [guia](MELHORIAS_01_IMPLEMENTACAO.md) e
  [procedimento de ensaios](ENSAIOS_ALCANCE.md).
