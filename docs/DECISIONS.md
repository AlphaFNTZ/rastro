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

## Decisões da semana 1 registradas na `main` — 15/09/2026

Foram comitadas na `main` como D-06 a D-12 (`550d623`) e não chegaram a este ramo, que usou os
mesmos números para as semanas 2 e 3. No merge de 23/10/2026 viraram D-26 a D-32, nesta ordem
(D-06 → D-26, D-07 → D-27, …, D-12 → D-32), com o texto original.

### D-26 — Revisão de D-01: Kotlin puro, sem mistura com Java
- **Data:** 15/09/2026
- **Decisão:** A implementação do trio será 100% Kotlin, sem nenhum código de produção ou de teste em Java. As classes de domínio da Semana 1 (`DomainExceptions`, `IMURead`, `Posicao`, `Trajeto`) e seus testes JUnit já foram escritos inteiramente em Kotlin.
- **Alternativas consideradas:** Manter a redação original de D-01, que previa "consumir contratos e testes originais em Java" — herdada de uma leitura inicial do guia sobre o repositório de referência do Fragmented Learning.
- **Motivo:** Nosso domínio (segurança de trabalhador remoto com detecção de queda) é próprio do trio, não uma tradução do caso inspirador — não existe contrato ou teste de terceiros em Java para consumir. Escrever tudo em Kotlin desde o início evita por completo o atrito de interoperabilidade descrito na seção 6.2 do guia (`@JvmStatic`, `@Throws`, `@JvmOverloads`, tipos de plataforma nulos), simplesmente porque essa fronteira Java/Kotlin nunca existe no nosso repositório.
- **Consequências:** Nenhuma das armadilhas de interoperabilidade Java/Kotlin listadas no guia se aplica a este projeto. Se algum componente do pacote de *fakes* do professor vier a ser consumido e estiver em Java, esta decisão precisa ser revisitada com uma nova entrada.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

### D-27 — Revisão de D-02: remoção de corrotinas, ExecutorService/Thread em todas as camadas
- **Data:** 15/09/2026
- **Decisão:** Substitui a parte de D-02 que previa corrotinas na Camada 3 (Agente). A partir de agora, `Thread` e `ExecutorService` (`java.util.concurrent`) são usados em **todas** as camadas de concorrência do sistema, incluindo o laço do Agente. Nenhuma corrotina Kotlin é usada em nenhuma camada.
- **Alternativas consideradas:** Manter corrotinas na Camada 3 conforme D-02 original, usando `withTimeoutOrNull` para o orçamento de latência do agente.
- **Motivo:** Um único modelo de concorrência em todo o repositório reduz o atrito de revisão entre os três integrantes e evita o mapeamento adicional que o guia exige de quem usa corrotinas — declarar explicitamente qual despachante cada corrotina usa e como isso muda a interferência entre tarefas (seção 6.4). Com os três dominando igualmente `Thread`/`ExecutorService`, a arguição fica defensável por qualquer integrante, não só por quem escreveu o código do agente.
- **Consequências:** O orçamento de latência do Agente (Camada 3) será implementado manualmente via `Future.get(timeout, TimeUnit)` em vez do `withTimeoutOrNull` de uma linha, perdendo o cancelamento estruturado das corrotinas. Fica registrado que — como o próprio guia observa na seção 6.4 — interromper uma inferência nativa já em andamento não funciona de verdade em nenhum dos dois modelos: `Future.cancel(true)` faz o laço do agente seguir adiante, mas não impede a inferência de continuar consumindo processador até terminar. Isso precisa constar no relatório da AV2, não ser apresentado como resolvido.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

### D-28 — Atitude do IMU representada como escalar (inclinação), não pitch/roll/yaw completo
- **Data:** 15/09/2026
- **Decisão:** `IMURead` representa a atitude do aparelho como um único ângulo `inclinacaoGraus` (0°–180°, ângulo entre o eixo principal do aparelho e a vertical), em vez de pitch/roll/yaw completos ou um quatérnio.
- **Alternativas consideradas:** Atitude completa via pitch/roll/yaw (3 ângulos) ou quatérnio (4 componentes).
- **Motivo:** O `ThresholdDetector` da Camada 1 só precisa saber se o aparelho ficou plano (horizontal) depois de um pico de aceleração, para suspeitar de queda seguida de imobilidade — um escalar já carrega essa informação. Atitude completa adicionaria complexidade sem contribuir para o critério de aceitação da detecção de queda.
- **Consequências:** Se decidirmos refinar a detecção mais adiante (por exemplo, diferenciar queda para frente de queda lateral), o contrato de `IMURead` precisa mudar, não só a lógica do `ThresholdDetector` — isso deve ser tratado como nova decisão, não como extensão silenciosa do contrato.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo
- **Situação em 23/10/2026:** escrita quando a detecção de queda ainda estava no escopo da semana 1;
  ela saiu depois (D-21). O campo `inclinacaoGraus` continua no `IMURead`, sem uso na malha.

### D-29 — Limite de sanidade da magnitude de aceleração do IMU
- **Data:** 15/09/2026
- **Decisão:** `IMURead` rejeita, na construção, leituras com módulo de aceleração em qualquer eixo acima de 200,0 m/s² (~20,4 g) — constante `ACELERACAO_MAX_MPS2`.
- **Alternativas consideradas:** Usar o fundo de escala do datasheet do acelerômetro específico de cada aparelho do trio; não impor limite algum na Camada 1 e delegar toda a plausibilidade ao `Reconciler`/`GrossErrorDetector` da Semana 2.
- **Motivo:** É preciso uma barreira de sanidade já no objeto de domínio (capacidade 1), antes de qualquer reconciliação estatística (capacidade 2). Como datasheets variam por aparelho, 200,0 é um valor de bom-senso de engenharia, não uma especificação de hardware.
- **Consequências:** Precisa ser recalibrado com dados reais dos acelerômetros dos três aparelhos assim que a coleta começar (Semana 2/3). Um valor mal calibrado pode rejeitar picos de queda genuínos (falso negativo na entrada do detector) ou deixar passar ruído absurdo de sensor (falso positivo).
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo
- **Situação em 23/10/2026:** escrita quando a detecção de queda ainda estava no escopo da semana 1;
  ela saiu depois (D-21). O limite continua no `IMURead`; a recalibração com dados reais não foi feita.

### D-30 — Velocidade média do Trajeto usa a velocidade reportada pelo sensor GNSS
- **Data:** 15/09/2026
- **Decisão:** `Trajeto.velocidadeMediaMps()` calcula a média aritmética do campo `velocidadeMps` que cada `Posicao` já traz do sensor GNSS, em vez de recalcular a velocidade por distância/tempo entre pontos consecutivos (haversine).
- **Alternativas consideradas:** Recalcular velocidade a partir da distância geodésica (haversine) entre pontos consecutivos e do intervalo de tempo entre eles.
- **Motivo:** Manter `Trajeto` como estrutura de dados simples (capacidade 1), sem acoplá-la a uma fórmula de distância geodésica que pertence mais naturalmente à camada de reconciliação (capacidade 2, `Reconciler`) da Semana 2.
- **Consequências:** A acurácia dessa média depende inteiramente da acurácia que o próprio sensor GNSS reporta (`acuraciaVelocidadeMps`). Se o `Reconciler` da Semana 2 vier a corrigir a velocidade por mínimos quadrados ponderados, `Trajeto` pode precisar de um método adicional que use a velocidade reconciliada, não a bruta.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

### D-31 — Ordem cronológica como invariante estrutural de Trajeto
- **Data:** 15/09/2026
- **Decisão:** `Trajeto.comNovoPonto()` rejeita, com `InvalidTrajetoException`, qualquer `Posicao` cujo `timestampMs` seja anterior ao do último ponto já armazenado. Timestamps iguais são aceitos.
- **Alternativas consideradas:** Aceitar pontos fora de ordem e reordenar internamente a cada inserção; aceitar sem validação e deixar a responsabilidade inteiramente para quem chama.
- **Motivo:** Reordenar a cada inserção quebraria o custo esperado de `comNovoPonto` e a garantia de tempo linear/memória constante da capacidade 1. Falhar cedo com uma exceção específica é mais barato e mais seguro do que reordenar silenciosamente.
- **Consequências:** Empurra para a fonte de leitura (o futuro `GpsSource`/`FixSource` da Semana 4) a responsabilidade de nunca entregar leituras fora de ordem ao `Trajeto`. Se o sensor ou o SO entregar leituras atrasadas ou reordenadas, isso precisa ser filtrado antes de chegar aqui.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

### D-32 — Trajeto não é data class
- **Data:** 15/09/2026
- **Decisão:** `Trajeto` é uma classe comum, com construtor privado e fábrica `vazio()`, e não uma `data class` como `Posicao` e `IMURead`.
- **Alternativas consideradas:** Declarar `Trajeto` como `data class`, igual às demais estruturas de domínio.
- **Motivo:** `Trajeto` tem comportamento (janela deslizante, recorte temporal) e identidade de estrutura — não é um valor comparável por igualdade de conteúdo como `Posicao`/`IMURead`. Os métodos de brinde de uma `data class` (`equals`/`hashCode`/`copy` por conteúdo) não fazem sentido semântico aqui e poderiam mascarar bugs: dois `Trajeto` com os mesmos pontos não são necessariamente "a mesma coisa" no sentido que importa para o sistema.
- **Consequências:** Nenhuma perda funcional; é decisão de modelagem a manter se novas estruturas com comportamento (como o futuro `CircularTrackBuffer` da Semana 4) forem criadas.
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

## Registro de implementação AV1 (S4–S5) — 21/10/2026

As entradas D-17 a D-21 foram redigidas e implementadas com assistente de IA
(declaração no relatório, seção 8). Como todas as decisões do grupo, foram tomadas em
conjunto, com a concordância dos três integrantes.

### D-17 — Reconciliação cinemática por mínimos quadrados ponderados
- **Data:** 21/10/2026
- **Decisão:** reconciliar janelas de 10 fixos GPS consecutivos sob a restrição
  pₐ(k+1) − pₐ(k) − ½(vₐ(k) + vₐ(k+1))·Δtₖ = 0 por eixo (leste e norte), com p em
  metros na projeção local, vₐ = componente da velocidade (módulo × seno/cosseno do
  rumo) e Δt em segundos do relógio monotônico. Solução ŷ = y − Σ·Aᵀ·(A·Σ·Aᵀ)⁻¹·A·y.
  Σ vem só do sensor: σ² = r68²/(−2 ln 0,32) na posição; propagação de (σs, σθ) na
  velocidade; sem rumo, componente com média 0 e variância σs² + s²/2.
- **Alternativas consideradas:** filtro de Kalman (estima estado, exige modelo de
  processo e ruído Q arbitrados — não é reconciliação de medidas); restrição de distância
  |Δp| = s·Δt (não linear e descarta a direção); mínimos quadrados sem peso (corrige igual
  a medida boa e a ruim).
- **Motivo:** com Δt conhecido a restrição é linear nas medidas, usa a velocidade Doppler
  (medida independente da posição) e dá solução fechada sem inverter Σ.
- **Consequências:** precisa de GNSS a 1 s (D-20). Não há reconciliação da IMU: a API
  não informa sigma por eixo (D-07 continua). Janela pequena → V = A·Σ·Aᵀ de 18 × 18.
- **Decidido por:** os três integrantes

### D-18 — Erro grosseiro por teste de medida com eliminação serial
- **Data:** 21/10/2026
- **Decisão:** zⱼ = |aⱼ|/√Wⱼⱼ (ajuste normalizado, W = Σ·Aᵀ·V⁻¹·A·Σ); se max zⱼ passa do
  limiar de Šidák para 4N medidas a α = 5% (≈ 3,2 com N = 10), retira-se o fixo e
  reconcilia-se de novo, até 3 eliminações. O teste global γ = rᵀ·V⁻¹·r é registrado.
- **Alternativas consideradas:** só o teste global (diz que há erro, não onde); teste
  nodal nas restrições (aponta a restrição, não a medida); limiar fixo 1,96 por medida
  (≈ 87% de falso alarme numa janela de 40 medidas).
- **Consequências:** nos fixos das pontas há uma restrição por eixo e o erro não é
  localizável; o ensaio injeta saltos no interior e a reprodução usa janela deslizante.
- **Decidido por:** os três integrantes

### D-19 — Ensaio reprodutível com semente declarada
- **Data:** 21/10/2026
- **Decisão:** `InjetorFalhas` com `java.util.Random(semente·31 + hash(rótulo))` — algoritmo
  especificado na documentação Java, igual em qualquer JVM/Android. Semente 20261020.
  Degradações: ruído gaussiano extra (acurácia declarada inalterada), perda de fixos e
  salto de posição. Registro em CSV com campo vazio para ausência (`RegistroFixCsv`).
- **Alternativas consideradas:** `kotlin.random.Random` (algoritmo não especificado entre
  versões); um único gerador para o ensaio todo (a ordem dos ensaios mudaria os números).
- **Decidido por:** os três integrantes

### D-20 — Aquisição da AV1: gravador, GNSS a 1 s, IMU automática e wake lock
- **Data:** 21/10/2026
- **Decisão:** `GravadorAquisicao` grava `gnss_*.csv` (um fixo por linha) e
  `aquisicao_*.csv` (1 Hz: leituras IMU, taxa, maior intervalo, fixos, tela, bloqueio,
  bateria, temperatura) em `Android/data/com.example.rastro/files/registros`. O serviço
  inicia a IMU junto com o GNSS, pede fixos a cada 1 s (antes 5 s) e mantém
  `PARTIAL_WAKE_LOCK` (limite 4 h) enquanto grava. A notificação pública mostra a
  contagem de fixos na tela de bloqueio.
- **Motivo:** a capacidade 4 exige log de 30 min com o aparelho bloqueado; sem wake lock o
  acelerômetro (sensor não-wakeup) pode parar com a CPU suspensa.
- **Consequências:** maior consumo de bateria, medido no próprio log. Posição pessoal fica
  em claro no armazenamento do app até a cifragem da AV2 (D-03) — limitação declarada.
  Os registros copiados para o repositório (público) devem ser gravados no campus.
- **Decidido por:** os três integrantes

### D-21 — Detecção de queda fora do escopo
- **Decisão:** o grupo retirou a detecção de quedas; o docente informou que ela não é
  obrigatória. As capacidades 1–5 da ficha não dependem dela. O RuleEngine da capacidade 8
  (AV2) precisa de nova definição, ligada à rede.
- **Data:** não registrada na época. O docente avisou, na fase da proposta, que a detecção de queda
  não era obrigatória; o grupo a retirou do escopo depois da semana 1, o que explica as D-28 e D-29
  ainda falarem nela. A entrada foi escrita em 21/10/2026.
- **Decidido por:** os três integrantes

## Registro dos ensaios reais da AV1 — 22/10/2026

### D-22 — Fase entre o consumidor IMU e o registro de 1 Hz
- **Data:** 21/10/2026 (correção) · 22/10/2026 (registro)
- **Decisão:** o registro de 1 Hz começa 1,5 s depois de iniciar (`PRIMEIRO_REGISTRO_MS`),
  meio período depois do consumidor IMU, e, se a janela estiver vazia, espera até 100 ms
  (`ESPERA_IMU_VAZIA_MS`) **fora** do monitor antes de gravar a linha.
- **Motivo:** o consumidor roda com atraso fixo (período = 1 s + tempo de execução) e o
  registro com taxa fixa; a fase entre os dois escorrega e, quando cruza, uma linha sai com
  imu_janela = 0 e a seguinte com o lote atrasado. Não é corrida: nenhuma leitura se perde e
  todo acesso continua dentro do monitor. A espera fica fora do monitor para não segurar o
  produtor nem o GNSS.
- **Alternativas consideradas:** as duas tarefas no mesmo executor (acopla o log ao
  consumidor, que é o que o log precisa observar); o registro com atraso fixo também (a fase
  escorregaria do mesmo jeito).
- **Consequências:** o jitter entre as duas tarefas continua existindo e é medido na AV2
  (capacidade 6). O commit `9ffba37` desfez a correção por engano ao restaurar a acentuação; a
  sessão de 15h20 de 22/10 foi gravada sem ela (4 linhas sem lote em 2170). A correção foi
  restaurada em `b648185`.
- **Resultado medido (22/10, sessão de 21h12, com a correção):** o registro esperou os 100 ms em
  trechos inteiros, e mesmo assim houve 4 linhas sem lote em 2280. A espera só segura enquanto o
  atraso do consumidor é menor que 100 ms; com atraso fixo, a fase escorrega de 1 a 2 ms por ciclo
  e a linha vazia volta a cada 9 a 12 min. **Correção de verdade, para a AV2:** consumidor com taxa
  fixa (`scheduleAtFixedRate`), mantendo as duas tarefas meio período defasadas o tempo todo.
- **Decidido por:** os três integrantes; correção proposta por Juliano Cesar Ferreira Ramos (com o Codex, 21/10/2026)

### D-23 — GPS com a tela apagada: declarar a localização em segundo plano
- **Data:** 22/10/2026
- **Observação:** no Samsung SM-S926B, o GPS para ~180 s depois que a tela apaga e volta
  quando ela acende (três ocorrências na sessão bloqueada; a mais longa, 734,5 s). A IMU e o
  registro seguem; o provedor de rede entrega posição a cada 20 s, com r68 mediano de 100 m.
  A otimização de bateria do Rastro já estava em "Irrestrito" e o serviço é do tipo `location`;
  das três verificações da seção 12 do roteiro da disciplina, faltava a permissão de localização
  em segundo plano: o app não a declarava, e o Android nem oferecia "Permitir o tempo todo".
- **Decisão:** declarar `ACCESS_BACKGROUND_LOCATION` no manifesto (`32e955e`). Com a permissão
  concedida à mão, a sessão de 21h12 de 22/10 (36,3 min bloqueada) teve fixo `gps` em 87% do
  trecho, contra 62% antes. Ainda houve uma pausa de 271,6 s, que começou ~180 s depois de a tela
  apagar e terminou sozinha, com a tela apagada; depois o GPS seguiu contínuo por ~29 min. A causa
  dessa pausa não foi identificada. Num teste curto no SM-S938B, a economia de energia ligada
  cortou o GPS mesmo com a permissão concedida. A sessão de 15h20 foi mantida em
  `dados/bloqueado_antes_d23` como evidência do problema.
- **Alternativas consideradas:** pedir a permissão dentro do app agora (fluxo novo em duas
  etapas e outra sessão de 35 min, sem tempo antes da entrega; fica para a AV2); trocar a fonte
  por `FusedLocationProvider` (depende do Google Play Services, e o projeto não usa serviços de
  nuvem).
- **Consequências:** sem a permissão, um SOS disparado com a tela apagada há mais de 3 min sai
  com a posição de rede (~100 m); a covariância vinda da acurácia declarada já reduz o peso desses
  fixos, e a reconciliação só usa fixos `gps`. Com a correção, a permissão ainda precisa ser
  concedida à mão (Configurações › Aplicativos › Rastro › Permissões › Localização) e a economia
  de energia precisa estar desligada. Na AV2: pedir a permissão no app, num passo separado da
  localização de primeiro plano, e investigar a pausa que ainda acontece ~3 min depois de a tela
  apagar.
- **Decidido por:** os três integrantes

### D-24 — Janelas com posição retida ficam fora das evidências
- **Data:** 22/10/2026
- **Decisão:** `Janelas.retida` marca a janela em que todos os fixos têm a mesma posição e
  velocidade zero; o `EvidenciasAv1Test` descarta essas janelas e declara quantas foram (5 de
  90 na caminhada, todas do início, com o aparelho ainda parado).
- **Motivo:** nelas A·y = 0 antes de reconciliar: a tabela de resíduo mostraria 0 → 0, e uma
  falha injetada seria detectada com facilidade artificial, inflando a sensibilidade.
- **Alternativas consideradas:** manter todas as janelas (números melhores e menos honestos);
  cortar o início do registro à mão (critério não reproduzível).
- **Decidido por:** os três integrantes

### D-25 — Coordenadas deslocadas na sessão gravada fora do campus
- **Data:** 22/10/2026
- **Decisão:** a sessão `20261022_211230` foi gravada fora do campus. Antes de publicar, a
  latitude e a longitude de todos os fixos (gps e rede) foram somadas a um mesmo deslocamento
  constante, que não está no repositório; o arquivo original fica com o grupo. As demais colunas
  não mudam.
- **Motivo:** o repositório é público, e a posição real de onde a pessoa estava é dado pessoal
  (LGPD). As análises usam posições relativas à média ou entre fixos vizinhos; o deslocamento só
  muda o fator cos(latitude) da projeção local, e a dispersão leste mudou menos de 0,1%. Os
  valores antes e depois não são publicados: a razão entre eles revela a componente norte do
  deslocamento.
- **Alternativas consideradas:** publicar o original (expõe o local); não publicar a sessão (os
  resultados de 4.4 e 4.5 deixariam de ser reproduzíveis).
- **Decidido por:** os três integrantes
