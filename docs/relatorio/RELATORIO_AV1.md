---
title: "Rastro — Relatório parcial da AV1"
subtitle: "GAT108 Automação Avançada · Projeto Integrador · Capacidades 1 a 5"
date: "23/10/2026"
---

<!--
Fonte do relatório. Os blocos "incluir: arquivo" são substituídos pelo script
scripts/gerar_documentos_av1.py pelas tabelas que o EvidenciasAv1Test gera a partir
dos registros reais; enquanto o arquivo não existe, aparece um aviso de pendência.
-->

# 1. Identificação

| | |
|---|---|
| Grupo | Rastro (trio) |
| Integrantes | Juliano Cesar Ferreira Ramos · Andre Bueno Ocanha · João Paulo Silva |
| Repositório | https://github.com/AlphaFNTZ/rastro |
| Commit entregue | informado no corpo do e-mail de entrega (um commit não pode conter o próprio hash) |
| Domínio | Comunicação descentralizada em áreas sem cobertura (rede mesh entre Androids) |
| Grandeza física da camada 1 | Posição e velocidade GNSS com a acurácia informada pelo receptor; aceleração em 3 eixos |

**Papel de cada integrante nas semanas 1 a 5.** As decisões foram tomadas pelos três, em conjunto.

- **João Paulo Silva** — domínio e contratos da semana 1 (`Posicao`, `IMURead`, `Trajeto`, exceções e
  testes), ajustes da proposta e registro de decisões da semana 1; nas semanas 4 e 5, a integração da
  AV1: reconciliação, detector de erro grosseiro, ensaio reprodutível, gravador, tela Ensaio AV1,
  evidências, relatório e slides, com o assistente de IA (seção 8).
- **Juliano Cesar Ferreira Ramos** — semanas 2 e 3: incerteza do GNSS, buffer circular e teste de
  corrida, a malha (Wi-Fi Direct, Nearby, roteamento, serviço em primeiro plano), as melhorias 01 a 03
  e as telas; na AV1, compilou, testou e instalou o aplicativo no aparelho, propôs a correção D-22 e
  fez as coletas.
- **Andre Bueno Ocanha** — nas semanas 1 a 3, participou das decisões registradas (D-01 a D-05 e
  D-26 a D-32), sem commits; nas semanas 4 e 5, trabalhou com João Paulo Silva em todos os commits da
  AV1, ajudando a escrever e conferindo, e por isso é coautor deles; revisor da pull request desta
  entrega.

# 2. O que foi implementado

Levantado no histórico do Git (todos os ramos) em 23/10/2026.

**João Paulo Silva** — semana 1, commit `240077b` (13/09): `Posicao`, `IMURead`, `Trajeto`
e `DomainExceptions` (exceções seladas `InvalidSensorReadException` e
`InvalidTrajetoException`), com `PosicaoTest`, `IMUReadTest` e `TrajetoTest`;
`7039bfe` (13/09) ajustes na proposta; `550d623` (15/09) registro de decisões da semana 1.

**Juliano Cesar Ferreira Ramos** — semana 2, `1613b5b`/`dc7ff92` (22/09): `CovarianciaGnss`,
`IncertezaImu`, `CircularImuBuffer`, `ImuSensorSource`, `LocationMapper` e o teste de corrida
de dez rodadas; semana 3, `09cb7ad`/`6713ba7` (24/09): `NearbyTransport`, `MeshRouter`,
`RastroService` em primeiro plano; melhorias 01 a 03 e telas (`51ef36d` a `3ee9e20`, 24 a 30/09).

**Andre Bueno Ocanha** — semanas 1 a 3: decisões D-01 a D-05 e D-26 a D-32, sem commits. Semanas 4
e 5: trabalhou pessoalmente com João Paulo Silva em todos os commits da AV1, ajudando a escrever e
conferindo; é coautor (`Co-authored-by`) dos 20 commits de 21 e 22/10/2026 (lista em 6.3).

**Implementação das semanas 4 e 5 (esta entrega)** — pacote `com.example.rastro.av1`:
`Matriz`, `Estatistica`, `RegistroFix`/`RegistroFixCsv`, `RegistroAquisicao`/`RegistroAquisicaoCsv`,
`ProjecaoLocal`, `ModeloIncerteza`, `Reconciliador`, `DetectorErroGrosseiro`, `Janelas`,
`InjetorFalhas`, `AnaliseEstatica`, `AnaliseAquisicao`, `TabelaResiduos`,
`DemonstracaoDetector`, `EnsaioSensibilidade`, `SaidaEvidencias`, `ReprodutorRegistro`,
`GravadorAquisicao` e `EnsaioActivity`; ligações em `RastroService`, `MainActivity`,
`ConfiguracaoActivity`, `LocalizacaoSos` e no manifesto; 11 classes de teste em
`app/src/test/.../av1`; scripts `av1_evidencias.ps1` e `av1_copiar_registros.ps1`.
Código gerado com assistente de IA (seção 8). Comitado por João Paulo Silva em 21/10/2026, com
Andre Bueno Ocanha como coautor: `ac1c93c`, `3418956`, `d2178d1`, `2c49a56`, `7815b3f`, `216a99b`,
`b09c873` e `b9c70d3`; `9ffba37` (acentuação do `GravadorAquisicao`) não tem coautor. Juliano Cesar Ferreira Ramos compilou o aplicativo, rodou a
suíte completa e instalou no aparelho em 21/10/2026 (`cb0468d`, que traz também a correção de
fase do gravador, D-22). As evidências 4.2 e 4.3 foram geradas de novo por João Paulo Silva com
`scripts/av1_evidencias.ps1` no commit `710ef50`, com a árvore limpa. As coletas físicas foram feitas pelo grupo em
22/10/2026 no campus da UFLA; a sessão bloqueada de 21h12 foi gravada por Juliano, fora do
campus (coordenadas deslocadas, 4.4).

# 3. Fundamentação

## 3.1 Estruturas de capacidade fixa

`Trajeto` guarda no máximo `capacidade` posições e descarta a mais antiga: a memória não
cresce com o tempo de operação, que num nó de borda é ilimitado. Comprimento é O(1);
velocidade média e recorte temporal são uma varredura, O(n) com n ≤ `capacidade`. A
inserção copia a janela (O(n)) porque `Trajeto` é imutável — leitores concorrentes nunca
veem um estado intermediário. Essa é a operação que poderia ser melhor: com um anel
mutável protegido, como o `CircularImuBuffer`, a inserção cai para O(1).

`CircularImuBuffer` é o anel mutável: inserção O(1) sobre um vetor de tamanho fixo
(500 leituras ≈ 10 s a 50 Hz), descarte contado do mais antigo quando lota, e `snapshot`/
`drenar` em O(n) que devolvem uma **cópia**. Sem a cópia, o consumidor iteraria a mesma
lista que o produtor está sobrescrevendo, e o erro apareceria como leitura fora de ordem ou
`ConcurrentModificationException` intermitente — exatamente o tipo de falha que o teste de
corrida procura.

## 3.2 Incerteza a partir do sensor

O Android informa a acurácia horizontal como um raio r68 que contém o fixo verdadeiro com
68% de probabilidade, e não como desvio padrão. Com erro gaussiano isotrópico de desvio σ
por eixo, a distância ao centro segue uma Rayleigh, e
P(|e| ≤ r68) = 1 − exp(−r68²/(2σ²)) = 0,68, o que dá

::: formula
σ² = r68² / (−2·ln 0,32) ≈ r68² / 2,279
:::

onde σ² é a variância por eixo (m²) e r68 o raio informado (m). A velocidade vem como
módulo s (m/s) com acurácia σs e rumo θ (graus a partir do norte) com acurácia σθ. As
componentes vE = s·sen θ e vN = s·cos θ têm covariância por propagação de primeira ordem
Σv = J·diag(σs², σθ²)·Jᵀ, em que J = ∂(vE, vN)/∂(s, θ) é o jacobiano e σθ está em
radianos. Parado, o Android não informa rumo; aí a direção é desconhecida e cada
componente recebe média 0 e variância σs² + s²/2 (direção uniforme). Nenhum valor de
incerteza é arbitrado: se o fixo não traz acurácia, ele não entra na reconciliação.

A dispersão medida com o aparelho parado (4.5) mostra se o receptor é honesto. A **razão
de otimismo** é σ medido / σ declarado: acima de 1, o sensor promete mais precisão do que
entrega e a matriz Σ fica pequena demais — a reconciliação passa a confiar demais nas
posições e o detector dispara falsos alarmes (Apêndice A, linhas de ruído). O fator
`fatorCovariancia` do `ModeloIncerteza` permite inflar Σ pela razão medida.

## 3.3 Reconciliação: a restrição física e por que ela é linear

Entre dois fixos consecutivos k e k+1, o deslocamento tem que ser compatível com a
velocidade medida. Por eixo a ∈ {leste, norte}:

::: formula
pₐ(k+1) − pₐ(k) − ½·(vₐ(k) + vₐ(k+1))·Δtₖ = 0
:::

onde pₐ(k) é a posição medida do fixo k naquele eixo (m, projeção local em torno do
primeiro fixo da janela), vₐ(k) a componente da velocidade (m/s) e Δtₖ o intervalo entre
os fixos em **segundos** pelo relógio monotônico (`elapsedRealtimeNanos`). É a integração
trapezoidal da velocidade. Como Δt é conhecido com precisão de nanossegundos, a equação é
**linear nas medidas**. Empilhando as 4N medidas da janela no vetor y e as 2(N−1)
restrições na matriz A, a restrição é A·y = 0.

A reconciliação por mínimos quadrados ponderados resolve

::: formula
min (ŷ − y)ᵀ·Σ⁻¹·(ŷ − y)  sujeito a  A·ŷ = 0  ⇒  ŷ = y − Σ·Aᵀ·V⁻¹·r
:::

com ŷ as medidas reconciliadas, Σ a covariância das medidas (3.2), r = A·y o resíduo das
restrições antes da reconciliação (m) e V = A·Σ·Aᵀ a covariância desse resíduo. A correção
de cada medida, y − ŷ = Σ·Aᵀ·V⁻¹·r, é proporcional à variância dela: **a medida mais
incerta é a que mais se move**. O teste `a medida mais incerta recebe a maior correcao`
verifica isso com dois fixos de r68 = 2 m e 20 m: o segundo absorve cerca de 99% da correção.
Depois da reconciliação A·ŷ = 0 a menos de erro numérico (≈ 10⁻¹⁵ m), e a estatística
γ = rᵀ·V⁻¹·r segue χ² com m = 2(N−1) graus de liberdade se não houver erro grosseiro — com
dados sintéticos calibrados, a média de γ/m em 300 janelas foi 0,98 (Apêndice A).

**Alternativas descartadas.** Ponderar todas as medidas igualmente (mínimos quadrados
ordinários) distorceria justamente a medida mais confiável, puxando um fixo de 2 m tanto
quanto um de 20 m. O filtro de Kalman estima um estado e exige um modelo de processo com
ruído Q arbitrado; aqui o objetivo é corrigir as medidas usando só o que o sensor informa.
A restrição de distância |Δp| = s·Δt usaria o módulo da velocidade, mas é não linear e
descarta a direção, que é o que detecta um salto lateral.

## 3.4 Erro grosseiro: critério quantitativo de corte

Com o ajuste a = y − ŷ e sua covariância W = Σ·Aᵀ·V⁻¹·A·Σ, cada medida j tem resíduo
normalizado zⱼ = |aⱼ| / √Wⱼⱼ, que segue N(0,1) sem erro grosseiro (teste de medida). Como
uma janela de N = 10 fixos tem 40 medidas, testar cada uma a 1,96 daria 1 − 0,95⁴⁰ ≈ 87% de
falso alarme por janela. O limiar usa a correção de Šidák: β = 1 − (1 − α)^(1/n) e
z_crítico = Φ⁻¹(1 − β/2), com α = 5% a taxa de falso alarme por janela, n = 40 o número de
medidas e Φ⁻¹ o quantil da normal; resulta z_crítico ≈ 3,2. Se max zⱼ passa do limiar, o fixo
que contém a medida j é retirado e a janela é reconciliada de novo (eliminação serial, até 3).
Sem o detector, a reconciliação não sabe que uma medida está errada e **espalha** o salto
pelos vizinhos (4.7). Limitação: nos fixos das pontas há uma única restrição por eixo e todas
as medidas dela têm o mesmo z; o erro é detectado, mas não localizado. Por isso o ensaio injeta
saltos no interior e a reprodução usa janela deslizante, em que todo fixo passa pelo interior.

## 3.5 Concorrência e região crítica

O produtor é a `HandlerThread` `rastro-imu-50hz`, onde o `SensorManager` entrega as amostras;
o consumidor é a thread `rastro-consumidor-imu` de um `ScheduledExecutorService` que drena a
cada 1 s. A região crítica é o `CircularImuBuffer`: índices de início, quantidade, slots e o
contador de perdas mudam juntos e precisam ser vistos juntos — uma operação composta. Usamos
um único monitor (`synchronized`) porque todas as operações são curtas e nenhuma bloqueia
dentro dele. Descartamos `ConcurrentLinkedQueue` (não limita a capacidade nem conta descarte
de forma atômica com a inserção) e `ReentrantLock` (não precisamos de tentativa com tempo nem
de várias condições). O `GravadorAquisicao` tem uma segunda região crítica, também com monitor
único, entre a thread principal (GNSS), o consumidor (lote IMU) e a tarefa de 1 Hz.

Os testes no aparelho mostraram um efeito que não é corrida, mas aparece como uma: o consumidor
IMU roda com **atraso fixo** de 1 s (o período real é 1 s mais o tempo de execução) e o registro
de 1 Hz roda com **taxa fixa**. A fase entre as duas tarefas escorrega devagar e, quando cruza, uma
linha do registro fica sem lote (imu_janela = 0) e a seguinte recebe o lote atrasado. Nenhuma
leitura se perde. A correção (D-22) começa o registro meio período depois do consumidor e, se a
janela estiver vazia, espera até 100 ms **fora** do monitor pela entrega real.

A sessão de 21h12 mostrou que essa correção não basta. Nela, o registro esperou os 100 ms em
trechos inteiros (são linhas atrasadas exatamente 100 ms), e mesmo assim houve 4 linhas sem lote
em 2280, a mesma taxa da sessão sem a correção. A espera só segura enquanto o atraso do consumidor
em relação ao registro é menor que 100 ms. Com atraso fixo, a fase continua escorregando de 1 a
2 ms por ciclo, passa dos 100 ms, e a linha vazia volta, uma vez a cada 9 a 12 min. A correção de
verdade é rodar o consumidor com taxa fixa (`scheduleAtFixedRate`), o que mantém as duas tarefas
meio período defasadas o tempo todo. Ela fica para a AV2, junto com a medição do jitter
(capacidade 6).

O teste de corrida (10 rodadas, produtor, consumidor e observador simultâneos, 10 000
leituras num buffer de 64) verifica ordem, ausência de duplicata e
consumidas + descartadas = produzidas. Ele **prova** que, nessas execuções, nenhuma
intercalação produziu estado inconsistente; ele **não prova** ausência de corrida — só a
análise do código (todo acesso dentro do monitor) sustenta isso. Rodadas sem falha são
evidência empírica, não demonstração.

## 3.6 Aquisição periódica

A aquisição roda no `RastroService`, serviço em primeiro plano com tipo
`connectedDevice|location` e notificação persistente: o Android não o encerra ao bloquear a
tela. O GNSS pede um fixo a cada 1 s; o acelerômetro, 20 ms. O serviço em primeiro plano
não impede o processador de dormir, e o acelerômetro é sensor não-wakeup: por isso o
gravador mantém um `PARTIAL_WAKE_LOCK` durante a gravação. O log de 1 Hz registra, a cada
segundo, leituras e taxa efetiva da IMU, maior intervalo entre amostras, fixos acumulados,
tela ligada/bloqueio e bateria — é a evidência de que a tarefa periódica continuou. A
política de escalonamento é a do Linux/Android (CFS, sem prioridade de tempo real), então o
período é solicitado, não garantido; a medição de Cᵢ e a análise de escalonabilidade são da
AV2 (capacidade 6). O serviço em primeiro plano mantém o processo e a IMU, mas, sem a permissão de
localização em segundo plano, não manteve o GPS com a tela apagada (4.4, D-23).

## 3.7 Ensaio reprodutível

O `InjetorFalhas` cria um `java.util.Random` por (semente, rótulo do ensaio). O algoritmo
desse gerador é especificado na documentação Java, então a mesma semente produz os mesmos
números em qualquer JVM ou Android, e a ordem em que os ensaios rodam não muda o resultado.
Três degradações, as do Anexo A: **ruído** gaussiano extra nas posições sem mudar a acurácia
declarada (simula um sensor otimista), **perda** de fixos (a janela encurta e Δt cresce) e
**salto** de posição de magnitude conhecida (o erro grosseiro). A semente declarada é
20261020. O mesmo ensaio, rodado duas vezes, é comparado dentro do `EvidenciasAv1Test`;
se diferir, o teste falha.

# 4. Evidências

Comandos: `scripts\av1_evidencias.ps1` (gera 4.2 a 4.8) e o roteiro de `docs/ENSAIO_AV1.md`.

## 4.1 Telas implementadas

Capturas do aparelho usado nas coletas (Samsung SM-S926B), feitas em 22/10/2026 no campus da UFLA.

::: lado

<!-- figura: telas/01_tela_principal.jpg | Figura 1 — Tela principal 19 s depois de ativar: a segunda linha do status mostra fixos GNSS, leituras IMU com a taxa e o tempo de gravação; o ponto no mapa é a posição atual. -->

<!-- figura: telas/02_notificacao_bloqueio.jpg | Figura 2 — Tela de bloqueio aos 16 min 18 s da sessão de 15h20, anterior à correção D-23 (recortada: o fundo da tela de bloqueio foi removido). A IMU segue a 50 Hz; o contador parado em 238 fixos GNSS é o corte do GPS descrito em 4.4, que começou cerca de 3 min depois de a tela apagar. -->

<!-- figura: telas/03_ensaio_erro_grosseiro.jpg | Figura 3 — Tela Ensaio AV1 ao fim da reprodução da caminhada (908 fixos, semente 20261020, salto de 30 m no fixo 454), em duas partes da mesma lista de eventos. O salto foi detectado com z = 39,7; as outras 17 eliminações não foram injetadas. -->

:::

A reprodução da Figura 3 usa janela deslizante: cada fixo passa por até 10 janelas, contra uma
só nas janelas disjuntas de 4.6, e por isso aparecem mais alarmes do que na tabela. As 17
eliminações não injetadas formam quatro grupos, e todos caem onde o movimento muda de repente:
fixos 66–69, na saída do repouso, com o rumo girando 35° em 3 s; 227–232, com a velocidade
pulando de 0,74 para 1,05 m/s; 517–522, numa curva em que o rumo girou cerca de 180° em 3 s; e
695–697, logo depois dos 3 s em que o receptor informou velocidade 0 (fixos 692–694). Nesses
trechos, a velocidade Doppler, com acurácia declarada de 0,1 a 0,3 m/s, não fecha com o
deslocamento das posições. Pode ser atraso do filtro de posição do próprio receptor ou um erro real
da velocidade; sem verdade de campo, o método aponta a inconsistência, mas não diz qual das duas.

## 4.2 Saída do comando de teste

<!-- incluir: resultados/testes_unidade.md | resumo -->

## 4.3 Teste de corrida — dez execuções

<!-- incluir: resultados/corrida_10_execucoes.md -->

## 4.4 Aquisição com o aparelho bloqueado

Duas sessões de cerca de 36 min com o aparelho parado e a tela bloqueada: a de 15h20, gravada
antes da correção D-23, e a de 21h12, gravada depois dela. A tabela principal é a da sessão nova;
a da sessão anterior vem logo abaixo, para comparação. A sessão de 21h12 foi gravada fora do
campus. Por isso, antes de publicar, as coordenadas foram deslocadas por um vetor constante, que
não está no repositório. As análises usam só posições relativas, e o deslocamento muda a dispersão
em menos de 0,1%.

<!-- incluir: resultados/aquisicao.md -->

A tabela completa da sessão anterior está no Apêndice B.

| | Antes da D-23 (15h20) | Depois da D-23 (21h12) |
|---|---|---|
| Trecho contínuo com a tela bloqueada | 36,2 min | 36,3 min |
| IMU: taxa média e leituras descartadas | 50,0 Hz, 0 | 50,1 Hz, 0 |
| Parte do trecho com fixo `gps` seguido | 62% | 87% |
| Maior intervalo entre fixos `gps` | 734,5 s (só voltou quando a tela acendeu) | 271,6 s (voltou sozinho, com a tela apagada) |
| Maior intervalo sem nenhuma posição (qualquer provedor) | 20,1 s | 20,1 s |
| Consumo de bateria | 8,3 %/h | 5,0 %/h |

**Leitura.** Nas duas sessões a tarefa periódica não parou: nenhuma linha do registro com
intervalo acima de 1,1 s, acelerômetro a 50 Hz e nenhuma leitura descartada. As 4 linhas sem lote
IMU de cada sessão são o efeito de fase de 3.5; as leituras foram no lote seguinte.

**GPS antes da correção.** Cerca de 3 min depois que a tela apagava, o aparelho parava de entregar
fixos `gps`, e eles só voltavam no instante em que a tela acendia. Aconteceu três vezes, sempre
com o mesmo atraso de ~180 s; a mais longa durou 734,5 s. O processo continuou vivo (IMU e
registro seguiram), e o provedor de rede continuou entregando posição a cada 20 s, com r68
mediano de 100 m. Para a malha, isso quer dizer: **o nó nunca ficou mais de 20,1 s sem posição
para anunciar, mas, com a tela apagada há mais de 3 min, a posição anunciada num SOS tinha raio
de ~100 m em vez de ~1 m.**

**Causa e correção (D-23).** O roteiro da disciplina (seção 12, "A aquisição para quando bloqueio
a tela") manda conferir três coisas. Duas estavam atendidas: o serviço é do tipo `location` e a
bateria do Rastro estava em "Irrestrito". Faltava a terceira: o app não declarava a permissão de
localização em segundo plano, e por isso o Android nem oferecia a opção "Permitir o tempo todo".
Com `ACCESS_BACKGROUND_LOCATION` declarada e concedida, a sessão de 21h12 teve fixo `gps` em 87%
do trecho bloqueado. Houve uma única pausa, de 271,6 s, que também começou ~180 s depois de a
tela apagar. Desta vez ela terminou sozinha, com a tela ainda apagada, e depois o GPS seguiu
contínuo por cerca de 29 min, até o fim, sem nenhuma lacuna acima de 3,4 s. Não sabemos por que
essa primeira pausa aconteceu. Num teste curto em outro aparelho (Samsung SM-S938B, conferido só
pelo contador da notificação), a economia de energia ligada também cortou o GPS, mesmo com a
permissão concedida. Pedir a permissão dentro do app, num passo separado da localização de
primeiro plano, fica para a AV2; por enquanto ela é concedida à mão nas configurações.

## 4.5 Dispersão das leituras estáticas e razão de otimismo

<!-- incluir: resultados/estatico.md -->

::: lado

<!-- figura: resultados/dispersao_estatica.svg | Figura 4 — Sessão de 21h12 (tabela acima): posições com o aparelho parado, em relação à média. Tracejado: r68 médio declarado; contínuo: raio com 68% dos fixos. -->

<!-- figura: resultados/dispersao_estatica_antes_d23.svg | Figura 5 — Sessão de 15h20 (tabela no Apêndice B): o rastro de pontos à esquerda é a convergência dos primeiros 4 min, com o receptor declarando r68 de ~1 m. -->

:::

**Leitura.** As duas sessões dão razões opostas, e nenhuma das duas mede o ruído do receptor.
Na sessão de 21h12 a razão foi 0,56, com 88% dos fixos dentro do próprio r68, o que parece um
receptor pessimista. Mas 99% dos fixos repetem exatamente a posição anterior: com o aparelho
parado, o receptor retém a posição, e a dispersão medida é a da retenção. Na sessão de 15h20 a
razão foi 3,32, com só 4% dentro do r68. Ali, nos primeiros 4 min, o receptor declarava r68
mediano de 1,0 m e estava em média a 12,9 m da posição em que se estabilizou depois (86% desses
fixos fora do próprio r68); em seguida ele também reteve a posição (94% de fixos repetidos).

A conclusão que vale é qualitativa. Com um celular parado, o ensaio estático mede a retenção de
posição e, quando há, o viés da convergência inicial. **A acurácia declarada não cobre esse viés
lento.** Para a reconciliação, o que importa é o erro entre fixos vizinhos, e ele foi medido em
movimento, em 4.6.

## 4.6 Resíduo antes e depois da reconciliação

<!-- incluir: resultados/residuos_resumo.md -->

**Leitura.** Caminhada de 15 min no campus, com a tela ligada, sem lacuna de GPS: 85 janelas
úteis. O resíduo RMS das restrições cai de 0,10–1,28 m (mediana 0,21 m) para menos de 10⁻¹⁴ m em
todas, ou seja, A·ŷ = 0 a menos de erro numérico. A média de γ/m foi 0,24, bem abaixo de 1, e
nenhuma janela passou do χ² de 95%. Isso é coerente com 4.5: na diferença entre fixos de 1 s o erro
lento se cancela, e a acurácia declarada fica conservadora para a restrição. As 5 janelas
descartadas são do início, com o aparelho ainda parado e a posição retida. Na janela 65, sem
nada injetado, o detector retirou três fixos seguidos pela velocidade norte (z de 4,7 a 5,2); logo
antes deles, o receptor informou velocidade 0, sem rumo, por 3 s no meio da caminhada. Sem verdade
de campo não dá para dizer se foi uma parada real ou um corte do receptor, nem garantir que, com
três fixos envolvidos, a eliminação serial retirou os fixos certos.

## 4.7 Sem o detector de erro grosseiro, a reconciliação piora

<!-- incluir: resultados/demonstracao_resumo.md -->

**Leitura.** Com um salto de 30 m no fixo central, a reconciliação sem o detector afasta os
fixos vizinhos, que estavam corretos, em média 2,94 m da referência; com o detector, 0,07 m. O
fixo corrompido foi detectado e localizado nas 85 janelas.

## 4.8 Sensibilidade da detecção à degradação injetada

<!-- incluir: resultados/sensibilidade.md -->

**Leitura.** A detecção começa a falhar entre 2 m (6%) e 5 m (92%): abaixo de ~5 m, um salto
se confunde com o ruído que o próprio receptor declara (r68 mediano de 1,5 m na caminhada). A
perda de fixos, até 50%, não muda a detecção; só deixa janelas inviáveis. O limite real do método
aparece no ruído: 1 m de ruído branco que o sensor **não declara** já gera 33% de falso alarme, e
2 m, 94%. O detector é tão bom quanto a honestidade da acurácia informada — o mesmo ponto de 4.5.

# 5. Integração

O projeto é independente: não consumimos código de outras duplas. Não há fakes em uso na
aplicação. Nos testes, `MeshRouter` e o repasse de mensagens rodam sobre transportes falsos
em memória (`ForwardNetwork`), e as classes da AV1 rodam na JVM com dados sintéticos
(`Sintetico`) e com os registros reais. A ficha de proposta previa reconciliação com GNSS e
detecção de falha injetada no GNSS isolando a medição (capacidade 2) e perda de dados com
semente (capacidade 5): é o que foi entregue. A detecção de quedas saiu do escopo (D-21).

# 6. Autoria por integrante

Levantado com `git log --all` em 23/10/2026, pela autoria e pelas linhas `Co-authored-by`. Os
commits das semanas 4 e 5 foram feitos na conta de João Paulo Silva, com coautoria; o código dessa
fase foi gerado com assistente de IA (seção 8).

## 6.1 João Paulo Silva

- **Semana 1.** `240077b` (13/09): `Posicao`, `IMURead` (`magnitude`), `Trajeto` (`vazio`,
  `comNovoPonto`, `velocidadeMediaMps`, `recorteTemporal`, `pontosOrdenados`) e `DomainExceptions`,
  com `PosicaoTest`, `IMUReadTest` e `TrajetoTest`; `7039bfe` (13/09), proposta; `550d623` (15/09,
  na `main`), decisões da semana 1 (D-26 a D-32).
- **Semanas 4 e 5**, pacote `av1`: `Reconciliador` (`reconciliar`, `restricoes`, `medidas`),
  `ProjecaoLocal`, `ModeloIncerteza`, `Matriz`, `Estatistica`, `DetectorErroGrosseiro` (`analisar`),
  `Janelas` (`disjuntas`, `retida`, `filtrar`), `InjetorFalhas` (`ruido`, `perda`, `salto`),
  `RegistroFix`/`RegistroFixCsv`, `RegistroAquisicao`/`RegistroAquisicaoCsv`, `AnaliseEstatica`,
  `AnaliseAquisicao`, `TabelaResiduos`, `DemonstracaoDetector`, `EnsaioSensibilidade`
  (`saltoMinimoDetectavel`), `ReprodutorRegistro` (`passo`), `SaidaEvidencias`, `GravadorAquisicao`
  (`iniciar`, `registrarFix`, `registrarImu`, `parar`) e `EnsaioActivity`; 11 classes de teste; a
  localização em segundo plano no manifesto (D-23).
- **Commits.** 21/10: `ac1c93c`, `3418956`, `d2178d1`, `2c49a56`, `7815b3f`, `216a99b`, `b09c873`,
  `b9c70d3` e `9ffba37`. 22/10: `b648185`, `32e955e`, `61946bc`, `271b8c2`, `3a8781b`, `a97e44e`,
  `be662d1`, `c78600a`, `be2de33`, `710ef50`, `678d58c` e `353bc47`. Nesta entrega, `6addf61` (merge
  da `main`, D-26 a D-32) e o commit de preenchimento final.

## 6.2 Juliano Cesar Ferreira Ramos

- **Base do projeto.** `1c6a1f5` e a documentação inicial (30/08).
- **Semana 2.** `1613b5b` e `dc7ff92` (22/09): `CovarianciaGnss` e `IncertezaImu`, `CircularImuBuffer`
  (`adicionar`, `snapshot`, `drenar`) com `CircularImuBufferTest` e o teste de corrida
  `CircularImuBufferConcurrencyTest`, `CovarianciaTest`, `ImuSensorSource`, `LocationMapper`,
  `Mensagem`, `CanalTcp`, `Transporte` e `WifiDirectTransport`.
- **Semana 3.** `09cb7ad` e `6713ba7` (24/09): `NearbyTransport`, `MeshRouter` (`originar`,
  `receber`), `IdentidadeNo` e `RastroService`, o serviço em primeiro plano.
- **Melhorias e telas** (24 a 30/09, `51ef36d` a `3ee9e20`): mapa offline (`MapaRastro`,
  `MapasOffline`, `PerfilAlcance`), SOS (`LocalizacaoSos`, `HistoricoSessao`), conversa e custódia
  (`ChatProtocol`, `ChatStore`, `CustodyEngine`, `ForwardEngine`), `QrScanActivity` e as telas.
- **AV1.** `cb0468d` (21/10): compilação, suíte completa, instalação no aparelho, primeiras dez
  execuções do teste de corrida e a correção de fase do `GravadorAquisicao` (D-22). Coautor de
  `61946bc`, `b648185` e `be662d1` (22/10) e dos dois commits desta entrega; gravou a sessão bloqueada
  de 21h12.

## 6.3 Andre Bueno Ocanha

- **Semanas 1 a 3.** Consta em "Decidido por" das decisões D-01 a D-05 e D-26 a D-32; não tem commits
  nessas semanas.
- **Semanas 4 e 5.** Trabalhou pessoalmente com João Paulo Silva em todos os commits da AV1, ajudando a
  escrever e conferindo. Por isso é coautor (`Co-authored-by`) de 20 commits:
  - 21/10: reconciliação cinemática e detector de erro grosseiro (`ac1c93c`); injeção de falhas com
    semente e reprodução (`3418956`); testes da AV1 (`d2178d1`); gravador de aquisição com wake lock,
    IMU e GNSS a cada 1 s (`2c49a56`); tela Ensaio AV1 (`7815b3f`); scripts de evidências (`216a99b`);
    decisões D-17 a D-21 (`b09c873`); primeira versão dos slides e do relatório (`b9c70d3`);
  - 22/10: restauração da correção D-22 (`b648185`) e correção D-23 (`32e955e`); registros reais da
    coleta no campus (`61946bc`) e da sessão de 21h12 (`be662d1`); descarte das janelas com posição
    retida (`271b8c2`); análises e evidências (`3a8781b`, `c78600a`, `be2de33`, `678d58c`); versões
    finais do relatório e dos slides (`a97e44e`, `710ef50`, `353bc47`).

  É coautor também dos dois commits desta entrega.
- **Revisão.** Revisor da pull request de `origin/joao` para a `main`.

# 7. Limitações

- **Sem verdade de campo.** A referência da demonstração 4.7 é a reconciliação da janela
  limpa, não a posição verdadeira.
- **Erros de GNSS são correlacionados no tempo.** Fixos a 1 s não são independentes; γ não
  segue exatamente χ², e a dispersão estática de 30 min subestima erros de longo período.
- **Pontas da janela.** Um salto no primeiro ou no último fixo é detectado, mas não
  localizado (3.4).
- **IMU sem reconciliação.** A API padrão não informa σ por eixo do acelerômetro (D-07);
  a IMU entra só na aquisição periódica.
- **Período não garantido.** Android não é tempo real; Cᵢ e escalonabilidade ficam para a AV2.
- **GPS com a tela apagada.** Sem a permissão de localização em segundo plano, o GPS parava ~3 min
  depois que a tela apagava. Com ela (D-23), a cobertura foi de 87%, mas ainda houve uma pausa de
  271,6 s, sem causa identificada, que terminou sozinha (4.4). A permissão ainda é concedida à
  mão nas configurações, e a economia de energia do aparelho precisa estar desligada.
- **Linhas sem lote IMU.** A espera de 100 ms da D-22 não eliminou as linhas vazias (4 em 2280).
  A correção de verdade, o consumidor com taxa fixa, fica para a AV2 (3.5). Nenhuma leitura se
  perde.
- **Posição retida.** Parado, o receptor repete a posição (94% e 99% dos fixos nas duas sessões):
  o ensaio estático não mede o ruído do receptor, e a razão de otimismo saiu 3,32 numa sessão e
  0,56 na outra (4.5).
- **Um aparelho só.** As sessões gravadas são de um Samsung SM-S926B: duas paradas e uma
  caminhada. O teste no SM-S938B foi curto e sem registro; outro receptor pode se comportar de
  outro jeito.
- **Energia.** Wake lock, GNSS a 1 s e IMU contínua consumiram 5,0 e 8,3 %/h nas duas sessões
  bloqueadas (4.4); com cargas iniciais diferentes, os dois valores não são diretamente comparáveis.
- **Dado pessoal em claro.** O registro fica sem cifra no armazenamento do app até a AV2
  (`AesGcmVault`, D-03); os registros publicados foram gravados no campus.
- **Processo.** A `main` ficou no commit da semana 1 (`550d623`, 15/09). As semanas 2 a 5 foram
  feitas nos ramos `develop`, `origin/juliano` e `origin/joao`, sem a pull request revisada que o
  roteiro exige (seção 3.1). A primeira, de `origin/joao` para a `main`, é a desta entrega, aberta
  depois do prazo para revisão de Andre Bueno Ocanha. Não há integração contínua: os testes rodam com
  `gradlew test` e `scripts\av1_evidencias.ps1`.
- **Compilação Android.** As partes Android desta entrega não foram compiladas no ambiente
  do assistente (sem SDK); foram compiladas e testadas por Juliano Cesar Ferreira Ramos em
  21/10/2026 com `gradlew` e instaladas no aparelho (4.2).

# 8. Declaração de uso de IA

**Semanas 1 a 3.** João Paulo Silva usou o Claude (Anthropic) na semana 1: classes de domínio
`Posicao`, `IMURead`, `Trajeto` e `DomainExceptions`, com seus testes (`240077b`, 13/09); ajustes na
proposta (`7039bfe`); registro das decisões da semana 1 (`550d623`, 15/09). Juliano Cesar Ferreira
Ramos usou o Codex (OpenAI) nas semanas 2 e 3: requisitos e documentação da S2 (`1613b5b`, `dc7ff92`,
22/09) e da S3 (`09cb7ad`, `6713ba7`, 24/09); melhorias 01 a 03 e telas do aplicativo (`51ef36d` a
`3ee9e20`, 24 a 30/09).

**Semanas 4 e 5.** Usamos o Claude em 21/10/2026 para gerar o pacote `av1` (seção 2), as ligações no
serviço e nas telas, os testes da AV1, os scripts, o rascunho deste relatório e os slides. O
assistente compilou e executou na JVM, no ambiente dele (Kotlin 2.0.21, JUnit 4.13.2), os testes de
domínio, S2 e AV1 — 86 testes, zero falhas — e gerou o Apêndice A com dados sintéticos. Ele não teve
acesso ao aparelho; em 22/10/2026 leu os registros reais depois de copiados para o computador, rodou
as análises de 4.4 a 4.8 e propôs o descarte das janelas com posição retida (D-24) e as linhas de GPS
e de lote IMU da tabela 4.4. Juliano Cesar Ferreira Ramos usou o Codex em 21/10/2026 para compilar,
rodar a suíte, instalar e testar no aparelho e propor a correção de fase do `GravadorAquisicao`
(D-22). Em 23/10/2026, o Claude preencheu este relatório, o registro de decisões e o e-mail a partir
das respostas do grupo, trouxe as decisões da `main` (D-26 a D-32) e gerou de novo o PDF e os slides.

**O que cada integrante verificou por conta própria.**

- **João Paulo Silva:** rodou `scripts\av1_evidencias.ps1` com a árvore limpa no `710ef50` — 161
  testes, 0 falhas, e 10 de 10 execuções do teste de corrida sem falha (`678d58c`); conferiu no
  aparelho a reconciliação e o erro grosseiro na tela Ensaio AV1 (o salto de 30 m foi detectado com
  z = 39,7).
- **Juliano Cesar Ferreira Ramos:** compilou com `gradlew`, rodou a suíte e o teste de corrida e
  instalou no aparelho (`cb0468d`); testou o chat de mensagens de um aparelho para outro; gravou a
  sessão bloqueada de 21h12, já com "Permitir o tempo todo" (`be662d1`).
- **Andre Bueno Ocanha:** conferiu, junto com João Paulo Silva, a reconciliação e o detector de erro
  grosseiro (`ac1c93c`, `d2178d1`) e a aquisição de GNSS e IMU com a tela bloqueada, com a correção
  D-23 (`2c49a56`, `32e955e`).

# 9. Leitura em camadas

Exigida somente na AV2.

# Apêndice A — Verificação da implementação com dados sintéticos

<!-- incluir: validacao_sintetica.md -->

# Apêndice B — Tabelas completas

## B.1 Testes de unidade, por classe

<!-- incluir: resultados/testes_unidade.md -->

## B.2 Resíduo em todas as janelas da caminhada

<!-- incluir: resultados/residuos.md -->

## B.3 Demonstração sem e com o detector, em todas as janelas

<!-- incluir: resultados/demonstracao_detector.md -->

## B.4 Sessão bloqueada anterior à correção D-23

<!-- incluir: resultados/aquisicao_antes_d23.md -->

<!-- incluir: resultados/estatico_antes_d23.md -->
