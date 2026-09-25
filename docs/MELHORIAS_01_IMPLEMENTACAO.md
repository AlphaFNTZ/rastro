# Melhorias 01 — implementação e operação

Data: 25/09/2026. Requisitos: [documento original](../melhorias-aplicativo-01.markdown).

## SOS, localização e histórico

- Protocolo v3: identidade/nome no instante da emissão, hora UTC, posição opcional,
  precisão opcional, hora da leitura e idade monotônica calculada no emissor.
- Recepção de v1/v2 continua possível. Dados ausentes permanecem desconhecidos.
  Atualize todos os aparelhos: leitura retrocompatível não faz apps antigos lerem v3.
- Limite: 1024 bytes UTF-8 por mensagem, nomes de até 32 bytes. O decodificador
  verifica UUID, coordenadas finitas, intervalos, combinações de campos e UTF-8.
- A aquisição começa ao tocar em Iniciar Rastro, sem depender do diagnóstico GNSS.
  GPS/rede são consultados a cada 5 s, conforme provedores e permissões disponíveis.
  Latitude/longitude não exigem velocidade; `LocationMapper` mantém seu contrato.
- Posição recente: até 60 s por padrão, configurável na tela em 15/30/60/120/300/600 s.
  Esse limite é operacional e ainda precisa de avaliação de campo. A idade usa
  `elapsedRealtime`; a posição é histórica se a aquisição/provedor estiver parado.
- SOS é enviado imediatamente com posição recente, última conhecida ou sem posição.
  Em Androids antigos, o próprio Nearby pode exigir localização para descobrir nós;
  a recusa de GPS não bloqueia por si só o envio por um enlace já disponível.
- Horários exibidos no fuso local. Tempo de ACK e timeout (10 s) usam apenas o
  relógio monotônico local. ACK confirma recepção pelo aplicativo, não resgate.
- Histórico limitado a 100 eventos em memória no serviço, com nome registrado,
  UUID, tipo, SOS, recebimento e intermediário. Recriar a Activity preserva o estado;
  encerramento do processo não preserva a sessão. A deduplicação guarda 2048 IDs.
- Último SOS segue a ordem de primeira entrega local. Mensagem nova sem posição
  remove o marcador anterior; repetir o mesmo ID não muda a seleção enquanto o ID
  estiver no histórico de deduplicação. Desconectar a origem não apaga o evento.

## Mapa e preparação offline

MapLibre Native Android **13.6.1** integrado a XML/Material, compilado com minSdk 26,
AGP 9.3.3 e Kotlin 2.2.10. Posição local roxa, SOS vermelho, precisão preenchida e
alcance verde tracejado. Círculos usam distâncias geodésicas em metros. A precisão
horizontal do Android é uma estimativa de raio com confiança de 68%, não cobertura
de rádio. O círculo remoto permanece associado à leitura do SOS.

Fonte: [OpenFreeMap](https://openfreemap.org/), estilo Bright, dados OpenStreetMap e
OpenMapTiles. O provedor publica uso sem limites de requisições e distribui seus
dados para hospedagem própria. Atribuição é mantida na tela e no mapa. Não há chave
API nem uso do servidor público de tiles raster do OpenStreetMap para download.
O mapa online consulta o provedor; a transmissão de SOS continua sendo Nearby.

1. Com internet, navegue até a área de operação (ou use Minha localização).
2. Toque Preparar área visível; escolha zoom 8–14, 10–16 ou 12–16.
3. A área deve ter até 20 km por lado. Só é mantida uma região por vez.
4. Aguarde **Região preparada**. Estilo, fontes, sprites e tiles são armazenados pelo
   OfflineManager. Há controles de pausa/retomada, enquadramento e remoção da região.
5. Desative o acesso à internet, mantendo os rádios necessários à malha, e confira
   a região. Fora da área/zoom preparado, o aplicativo informa que o mapa pode faltar.
6. Após mudança de conectividade, Recarregar mapa-base permite nova tentativa. Sem
   estilo disponível em 10 s, usa fundo local com marcadores e mantém o resumo textual.

Orçamento de download: 256 MiB de recursos regionais e cache ambiente de 32 MiB.
A pausa é aplicada nos callbacks de progresso: downloads em trânsito e overhead do
SQLite podem exceder o orçamento; não é uma cota rígida de tamanho de arquivo.
Ao atingir o orçamento, remova a região e prepare uma menor. Downloads sobrevivem
à rotação, mas não são um serviço de transferência garantida com o processo morto;
retome pela interface se interrompidos. Recursos já concluídos permanecem em disco.

O mapa respeita exploração manual; somente as ações Minha localização/Ver SOS ou
o primeiro fix sem exploração anterior centralizam. Gestos dentro do mapa não
rolam o ScrollView. SOS permanece fora do mapa, acima do cartão cartográfico.

## Alcance estimado

Não há perfil fictício embutido. Importe um JSON de ensaio real conforme
[procedimento de calibração](ENSAIOS_ALCANCE.md). Sem perfil válido e sem posição no
último SOS, o controle permanece indisponível. A habilitação exige confirmação dos
modelos/condições e vale apenas para o SOS selecionado; novo SOS desliga a camada.
O app não consegue validar remotamente os modelos e as condições físicas: essa
confirmação é responsabilidade do operador a partir do ensaio documentado.

O raio deriva das distâncias aprovadas por taxa de ACK e prazo, em ordem crescente.
Exigem-se ao menos dez repetições por distância e taxa mínima de 90%. Falha em um
grupo impede usar distâncias maiores. O resultado é uma aproximação circular do
ensaio; não garante conectividade, não usa qualidade Nearby para inferir metros e
não converte rotas por intermediários em evidência de alcance direto.

## Validação e pendências

Resultado em 25/09/2026: **84 testes JVM e 7 testes instrumentados aprovados** no
Pixel API 37 (Android 17). Compilação do APK e lint concluídos, zero erros e 28
avisos não bloqueantes (incluindo avisos preexistentes de layout/dependências).
A reabertura offline foi verificada com o estilo cartográfico e as camadas do SOS
instaladas; a inspeção visual também cobriu o tema escuro e o estado sem posição.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

Testes JVM cobrem v3/v1/v2, Unicode/bytes, dados inválidos, ausência/idade da posição,
retransmissão/deduplicação, identidade e limite do histórico, seleção do último SOS,
tempos monotônicos, círculos e derivação de perfis. Testes instrumentados cobrem
posição Android sem velocidade, contrato original GNSS e recriação da Activity.

Comandos na raiz:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

O teste `MapaOfflineTest` acessa o provedor para baixar uma região mínima e reabrir
seu estilo com a conexão do MapLibre desativada. Ele depende de internet durante a
preparação e remove a própria região ao final. Consulte os relatórios em
`app/build/reports/` para o resultado da execução mais recente.

Permanecem **pendentes em aparelhos físicos**: topologia A → B → C, medições de
alcance, bateria, GPS real sob obstrução e operação prolongada com tela bloqueada.
Não foram produzidas medições reais ou declarações de cobertura. Persistência
sensível, autenticação, fila durável e rastreamento remoto continuam fora do escopo.

Referências: [Android Location](https://developer.android.com/reference/android/location/Location),
[OfflineManager](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/-offline-manager/index.html),
[OpenFreeMap e atribuição](https://openfreemap.org/).
