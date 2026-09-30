# Propostas de melhorias do Rastro — 01

Data: 24/09/2026.

Status: implementação de software realizada em 25/09/2026; consulte [implementação, uso e validação](docs/MELHORIAS_01_IMPLEMENTACAO.md). Ensaios físicos de rádio, calibração de alcance e consumo de bateria permanecem pendentes. Este documento substitui `melhorias-aplicativo-01.txt`; as seções abaixo preservam os requisitos da entrega.

## 1. Objetivo e decisões adotadas

Ampliar o SOS manual com identidade, localização e horário; melhorar o histórico de eventos; e apresentar no mapa a posição local e a posição associada ao último SOS recebido pela malha, inclusive por intermediários.

Decisões acordadas:

- Manter Kotlin, XML/AppCompat, Nearby Connections e a arquitetura de serviço atual.
- Mostrar inicialmente o círculo de precisão GPS associado à leitura de localização.
- Acrescentar uma camada opcional, tracejada, chamada **Alcance estimado**, baseada em medições de campo.
- Considerar obstáculos, terreno e modelos dos aparelhos na calibração dessa camada.
- Não interpretar recebimento por intermediários como comprovação de alcance direto.
- Deixar autenticação e pareamento dos nós como tarefa futura, sem bloquear esta entrega.
- Priorizar funcionamento sem internet, com mapas preparados previamente.

## 2. Contexto do código existente

O projeto Gradle fica na raiz e o módulo Android em `app/src`. A cópia `app/app` foi removida e não deve ser recriada.

| Componente | Situação observada | Mudança prevista |
| --- | --- | --- |
| `network/Mensagem.kt` | Protocolo v2, com leitura de v1; SOS sem posição, nome ou horário | Criar protocolo v3 com os dados do SOS |
| `network/MeshRouter.kt` | Flooding, deduplicação, limite de saltos e ACK | Preservar os novos dados nas retransmissões |
| `service/RastroService.kt` | Recebe GNSS e publica texto de diagnóstico; gerencia a rede | Guardar posição estruturada, eventos e último SOS |
| `sensors/LocationMapper.kt` | Exige precisão horizontal, velocidade e precisão de velocidade | Manter o contrato atual e separar posição para mapa/SOS |
| `MainActivity.kt` | Solicita permissões e renderiza o estado do serviço | Renderizar mapa e eventos estruturados |
| `res/layout/activity_main.xml` | Interface XML sem mapa | Incluir mapa, legenda e resumo do SOS |

Os caminhos Kotlin acima são relativos a `app/src/main/java/com/example/rastro`. O layout é relativo a `app/src/main`.

A leitura GNSS atualmente depende de uma ação específica da interface. O log mantém até 30 textos em memória, sem histórico durável. O envio de SOS depende de vizinho conectado, e o primeiro ACK tratado encerra a espera daquele SOS. ACK significa recebimento pelo aplicativo, não confirmação de atendimento ou resgate.

## 3. SOS com identidade, localização e horário

### 3.1. Dados do evento

Criar uma representação imutável do SOS. Os nomes abaixo são uma proposta, a ajustar durante a implementação.

| Campo | Finalidade |
| --- | --- |
| `id` | UUID do evento, preservado na retransmissão e na deduplicação |
| `origem` | UUID estável do aparelho que emitiu o SOS |
| `nomeOrigem` | Nome do aparelho no instante da emissão |
| `emitidoEmEpochMs` | Data e hora da emissão em UTC, em milissegundos |
| `localizacao` | Bloco opcional com latitude, longitude, precisão horizontal e horário da leitura |
| `idadeLocalizacaoNaEmissaoMs` | Idade da leitura calculada no aparelho de origem, quando disponível |
| `estadoLocalizacao` | Indicação de posição recente, antiga ou indisponível |

Manter tipo, versão, limite de saltos e referências de ACK necessários ao protocolo. Latitude e longitude devem existir juntas. Nunca usar `0,0` para representar localização ausente: é uma coordenada válida.

### 3.2. Aquisição e validade da posição

- Guardar a última posição válida em um modelo próprio no serviço.
- Não exigir velocidade ou precisão de velocidade para usar latitude e longitude.
- Preservar o `LocationMapper` e os cálculos de covariância existentes para sua finalidade original. Não enfraquecer seus contratos para atender ao mapa.
- Integrar a aquisição ao início explícito do monitoramento, após concessão das permissões, sem depender de um botão de diagnóstico para o SOS.
- Manter a aquisição no serviço; a Activity observa o estado e solicita permissões.
- Calcular a idade da leitura usando tempo monotônico no aparelho de origem. Não comparar relógios monotônicos de aparelhos diferentes.
- Definir um limite configurável de idade para considerar uma posição recente. Escolher e validar o valor na implementação e nos ensaios.
- Se houver apenas posição antiga, identificá-la como **Última posição conhecida**, mostrando sua idade na emissão.
- Se não houver posição, transmitir o SOS com **Localização indisponível**.
- Não bloquear o SOS aguardando indefinidamente uma posição GPS.
- Não apresentar posição antiga como atual após perda de GPS ou parada do serviço.

A frequência de aquisição deve equilibrar atualização e consumo de bateria. Permissão negada, localização aproximada, GPS desligado e ausência de leitura devem ter estados visíveis. Caso a primeira versão exija localização precisa, a recusa deve manter possível o SOS sem coordenadas, desde que exista enlace disponível.

### 3.3. Horários e retransmissão

- Armazenar horários absolutos em UTC e formatá-los no fuso do aparelho que exibe.
- Capturar separadamente o horário da emissão e o horário da leitura da posição.
- Registrar o recebimento no destinatário sem substituir a hora da emissão.
- Intermediários preservam nome, origem, posição e horários originais.
- Manter timeout e medição local de ACK em relógio monotônico.
- Não calcular latência de ponta a ponta apenas subtraindo horários de aparelhos distintos: seus relógios podem estar dessincronizados.

### 3.4. Evolução do protocolo

Implementar versão v3 com codificação, decodificação e validação explícitas. Preservar leitura de v1/v2 quando viável, representando campos ausentes como desconhecidos, sem inventar coordenadas ou horários.

Revisar as regras atuais que proíbem `nomeOrigem` em SOS e o limite de 512 caracteres do decodificador. Definir limites coerentes também em bytes no transporte, incluindo nomes Unicode. Validar coordenadas finitas e dentro dos intervalos geográficos, precisão válida, identificadores, campos opcionais e tamanho máximo.

Aplicativos antigos não passam a entender v3 porque o novo decodificador entende v1/v2. Na primeira entrega, atualizar todos os aparelhos do ensaio. Negociação de versões pode ficar para uma evolução.

## 4. Histórico de eventos

Substituir strings isoladas por eventos estruturados com instante de registro, tipo, identidade do aparelho envolvido, identificador do SOS e descrição.

Exemplo de apresentação:

```text
24/09/2026 19:42:08 — Equipe Alfa [a13f90bc]
SOS recebido — emitido às 19:42:03
Precisão da localização: 12 m — leitura feita 4 s antes da emissão
```

Requisitos:

- Exibir data, hora e nome, acrescentando UUID abreviado para distinguir nomes iguais.
- Identificar o emissor original de SOS retransmitido. Mostrar o intermediário separadamente, se necessário.
- Para mensagens antigas sem nome, usar o identificador abreviado como alternativa.
- Diferenciar envio, recebimento, ACK, timeout e eventos locais de conexão/serviço.
- Preservar o nome registrado no evento mesmo que o aparelho seja renomeado depois.
- Manter capacidade limitada de histórico.
- Não duplicar o registro se a mesma mensagem chegar por caminhos diferentes.

A primeira entrega pode manter eventos em memória, identificados como histórico da sessão. Para sobreviver ao encerramento do processo, será necessária persistência adicional, seguindo D-03 de `docs/DECISIONS.md`: SQLiteOpenHelper e proteção dos campos sensíveis.

Recriar a Activity não deve perder o estado enquanto o serviço estiver vivo; isso não exige, por si só, persistência em disco.

## 5. Mapa na tela principal

### 5.1. Conteúdo e interação

- Marcador da posição local, atualizado conforme novas leituras válidas.
- Marcador da posição associada ao último SOS recebido e círculo de precisão, quando disponíveis.
- Resumo com nome, UUID abreviado, emissão, recebimento, coordenadas e condição da localização.
- Ações **Minha localização** e **Ver SOS**.
- Não recentralizar o mapa a cada atualização quando o usuário estiver explorando outra área.
- Legenda distinguindo posição local, SOS, precisão e alcance estimado.
- Botão de SOS acessível sem exigir interação com o mapa.
- Coordenadas e informações textuais úteis mesmo sem mapa-base disponível.

O marcador remoto representa a posição **registrada no SOS**, não a posição atual do emissor. Rastreamento remoto contínuo exigiria atualizações adicionais e fica fora desta entrega.

### 5.2. Definição de último SOS

Adotar **último SOS recebido**, segundo a ordem de primeira entrega local. Exibir essa expressão na interface. Isso evita depender de sincronização entre relógios para ordenar eventos de emissores diferentes.

- Receber novamente o mesmo UUID não altera a seleção.
- Um novo SOS sem localização atualiza o resumo e retira o marcador anterior da posição de destaque. Não herdar suas coordenadas.
- Caso seja possível consultar SOS anterior com posição, identificá-lo como histórico.
- A saída do emissor da rede não apaga o evento recebido nem transforma sua posição histórica em indicação de conectividade atual.

### 5.3. Biblioteca e uso offline

Avaliar **MapLibre Native para Android**, integrado à interface XML. Validar a versão escolhida contra minSdk, AGP e dependências reais do projeto antes de consolidar a integração.

A biblioteca de renderização não substitui a fonte cartográfica. Escolher provedor ou pacote que permita o uso e armazenamento offline pretendidos, mantendo a atribuição adequada. Não assumir que qualquer servidor permite download em massa.

Preparar os mapas da região antes da operação sem internet, incluindo os recursos necessários ao estilo. Definir área, níveis de zoom e limite de armazenamento. Indicar quando uma região não foi preparada, mantendo os dados textuais do SOS disponíveis.

Tratar o ciclo de vida da visualização e os conflitos de gestos com o ScrollView existente. Não é necessário reescrever o aplicativo em Compose.

## 6. Precisão GPS e alcance estimado

### 6.1. Primeira etapa: círculo de precisão

Desenhar um círculo geográfico centrado na coordenada da leitura, com raio em metros correspondente à precisão horizontal informada pelo Android. A API Location expressa essa precisão como uma estimativa de raio com confiança de 68%, não uma fronteira exata que garanta a posição do aparelho.

Rotular como **Precisão da localização**. Esse círculo não representa alcance de Bluetooth/Wi-Fi nem deslocamento possível desde a emissão.

Se a precisão estiver indisponível, não inventar um raio. Se a leitura estiver antiga, apresentar essa condição no resumo associado ao círculo. Aplicar a mesma distinção à posição local e ao SOS.

### 6.2. Segunda etapa: camada opcional de alcance

Adicionar controle para **Alcance estimado**, com contorno tracejado e estilo distinto do círculo de precisão. Manter desativado inicialmente e não desenhar cobertura sem perfil de medição aplicável.

Requisitos:

- Basear a estimativa em ensaios físicos documentados, nunca em conversão arbitrária de qualidade baixa/média/alta para metros.
- Informar o perfil utilizado, suas condições e limitações.
- Considerar modelos dos aparelhos envolvidos, obstáculos, terreno, ambiente e orientação/posição durante as medições.
- Explicar que o círculo é uma aproximação: a cobertura real pode ser irregular e depende dos dois aparelhos.
- Usar a localização registrada no SOS como centro de referência, sem afirmar que o emissor permanece naquele local ou conectado.
- Diferenciar enlace direto observado de alcance por intermediários.

O Nearby utilizado fornece categorias de qualidade do enlace, não um raio geográfico. Receber A → B → C não comprova conexão direta entre A e C. Não desenhar a cobertura de A a partir da qualidade do enlace B → C.

Antes de gerar perfis, definir o critério de comunicação utilizável, por exemplo entrega e ACK dentro de um intervalo definido. Registrar repetições, distâncias, falhas, latência e condições. Os limites devem resultar dessas medições, sem fixar um raio arbitrário neste documento.

## 7. Sequência de implementação

| Etapa | Trabalho | Critério de conclusão |
| --- | --- | --- |
| 1 | Modelo de posição e aquisição independente da velocidade | Posição válida utilizável mesmo sem velocidade |
| 2 | Protocolo v3 e SOS completo | Dados originais preservados em múltiplos saltos |
| 3 | Último SOS e eventos estruturados | Histórico e seleção corretos, sem duplicatas |
| 4 | Mapa, marcadores e precisão | Visualização correta, incluindo indisponibilidade |
| 5 | Preparação e validação offline | Região preparada utilizável sem internet |
| 6 | Ensaios de alcance e perfis | Medições e critérios de aplicabilidade documentados |
| 7 | Camada opcional de alcance estimado | Tracejado, legenda e limitações explícitas |

Escolher fonte cartográfica e estratégia offline antes de consolidar a etapa 4. Manter aquisição e rede no serviço, protocolo e roteamento testáveis separadamente e apresentação na Activity. Não criar uma aquisição GPS paralela apenas para o mapa.

## 8. Validação e critérios de aceite

### 8.1. Testes automatizados

- Codificação/decodificação v3 e leitura de versões antigas com campos ausentes.
- Nomes Unicode, limites de tamanho e rejeição de dados inválidos.
- SOS sem posição, com posição antiga e com posição sem velocidade.
- Retransmissão preservando dados originais e alterando apenas campos de rota.
- Deduplicação, ACK e seleção do último SOS recebido.
- Relógios absolutos divergentes sem afetar timeouts monotônicos locais.
- SOS novo sem posição sem reutilizar o marcador anterior.
- Identidade correta nos eventos e capacidade limitada do histórico.

Executar na raiz:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Acrescentar testes instrumentados para permissões, ciclo de vida e integração com o mapa quando necessários. Testes JVM não comprovam o comportamento físico do rádio.

### 8.2. Testes em aparelhos

- Usar três aparelhos atualizados, com topologia A → B → C verificada.
- Confirmar em C nome, horário e coordenadas originais de A, sem substituição pelos de B.
- Testar GPS desligado, permissão negada/aproximada, demora da primeira leitura e posição antiga; manter envio quando existir enlace disponível.
- Conferir precisão, unidades, legenda, enquadramento e atualização dos marcadores.
- Testar sem internet na região preparada e fora dela, mantendo ativos os rádios necessários ao Nearby.
- Recriar a Activity e bloquear a tela, verificando estado e continuidade do serviço.
- Testar relógios diferentes e mensagens duplicadas/atrasadas.
- Medir consumo de bateria de GPS, mapa e rede.
- Comparar alcance estimado com ensaios documentados em ambientes distintos; ausência de perfil não pode gerar cobertura fictícia.

## 9. Tarefas futuras e limites de escopo

- **Autenticação e pareamento:** tarefa futura por decisão do usuário. A aceitação automática atual permanece nesta entrega; nome e UUID anunciados não passam a ser identidades autenticadas.
- Persistência durável e proteção dos dados em repouso, conforme D-03.
- Armazenamento e reenvio posterior quando não houver vizinhos. Este plano não cria fila durável nem entrega garantida automaticamente.
- Negociação de protocolo entre versões distintas.
- Sincronização dos relógios para comparar com confiança a ordem de emissão entre nós.
- Rastreamento remoto contínuo após o SOS, cancelamento e resolução dos alertas.

Não adicionar essas tarefas implicitamente à primeira entrega. Registrar resultados dos ensaios sem declarar cobertura ou confiabilidade ainda não demonstradas.

## 10. Referências

- [Decisões de arquitetura](docs/DECISIONS.md).
- [Implementação de rede e serviço S3](docs/README_Semana3.md).
- [Validação S3 existente](docs/VALIDACAO_S3.md).
- [Android Location: precisão e tempos](https://developer.android.com/reference/android/location/Location).
- [Nearby: qualidade do enlace](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/BandwidthInfo.Quality).
- [MapLibre Native Android: regiões offline](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/-offline-manager/index.html).
