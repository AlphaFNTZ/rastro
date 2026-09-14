# Semana 1 — Domínio, contratos e estruturas (Grupo Rastro)

## O que tem aqui

```
app/src/main/java/br/ufla/rastro/domain/
  DomainExceptions.kt   — RastroDomainException, InvalidSensorReadException, InvalidTrajetoException
  Posicao.kt            — leitura GNSS imutável (equivalente ao "Fix" do glossário)
  IMURead.kt            — leitura IMU imutável (aceleração 3 eixos + atitude)
  Trajeto.kt            — estrutura de capacidade fixa, imutável, memória constante

app/src/test/java/br/ufla/rastro/domain/
  PosicaoTest.kt
  IMUReadTest.kt
  TrajetoTest.kt
```

Ajuste o pacote `br.ufla.rastro.domain` para o `applicationId`/pacote-base real do
projeto de vocês antes de copiar os arquivos para o repositório (mova para o
diretório correspondente do seu `app/src/...`).

## Como rodar

```
./gradlew test --tests "*Posicao*"
./gradlew test --tests "*IMURead*"
./gradlew test --tests "*Trajeto*"
./gradlew test
```

Relatório em `app/build/reports/tests/testDebugUnitTest/index.html`.

## Como isso cobre o critério da capacidade 1 (Anexo A)

- **Invariantes validadas na construção, exceção específica**: `Posicao` e
  `IMURead` validam tudo em `init`, lançando `InvalidSensorReadException`;
  `Trajeto` lança `InvalidTrajetoException` para capacidade inválida, ordem
  cronológica violada ou recorte inválido. Nunca uma `IllegalArgumentException`
  solta.
- **Memória constante**: `Trajeto` nunca guarda mais que `capacidade` pontos —
  ao ultrapassar, descarta o mais antigo (janela deslizante).
- **Recorte temporal em tempo linear O(n)**: `recorteTemporal` faz uma
  varredura única sobre os pontos armazenados.
- **"Média" em O(n)**: `velocidadeMediaMps` também é uma varredura única.

## Decisões que valem uma entrada em `docs/DECISIONS.md`

Nenhuma delas é a única resposta certa — são escolhas que fiz para poder
entregar algo concreto; registrem se concordarem, mudarem, ou justificarem
diferente na arguição:

1. **Atitude como escalar único** (`inclinacaoGraus`, 0–180°) em vez de
   pitch/roll/yaw completos. Suficiente para "pico de aceleração + atitude
   horizontal" do RuleEngine; insuficiente se decidirem fazer algo mais fino
   com orientação depois.
2. **Limite de sanidade da aceleração** em `IMURead.ACELERACAO_MAX_MPS2 = 200.0`
   (~20,4 g) é um chute de engenharia, não um dado de datasheet. Vale
   recalibrar com os aparelhos reais da dupla.
3. **Velocidade média do Trajeto usa o campo `velocidadeMps` que o próprio
   sensor já reporta**, em vez de recalcular por distância/tempo entre pontos
   (haversine). Mais simples e evita acoplar `Trajeto` a uma fórmula de
   distância geodésica que pertence mais naturalmente ao `Reconciler` da
   Semana 2.
4. **Ordem cronológica é invariante de `Trajeto`**: um novo ponto não pode ter
   timestamp anterior ao último já armazenado (timestamps iguais são aceitos).
   Isso empurra para trás, na fonte (`FixSource`/`GpsSource`), a
   responsabilidade de nunca entregar leituras fora de ordem.
5. **Trajeto não é `data class`**: é uma estrutura com comportamento
   (janela deslizante, recorte), não um valor comparável por igualdade de
   conteúdo como `Posicao`/`IMURead`. Decisão de design, não exigência do
   enunciado.

## Sobre o teste de concorrência

`TrajetoTest` inclui um teste com `ExecutorService` + `CountDownLatch` (sem
corrotinas, conforme decidido) que dispara 20 threads chamando `comNovoPonto`
simultaneamente sobre a MESMA instância de `Trajeto`. Como a estrutura é
imutável, nenhuma delas escreve no estado compartilhado — o teste comprova que
a instância original permanece intocada. Essa é a propriedade que a Semana 4
(capacidade 3, `CircularTrackBuffer`, região crítica de verdade) vai construir
em cima, agora com um componente mutável e `synchronized`/`ReentrantLock`.

## Próximo passo (Semana 2)

`Reconciler` e `GrossErrorDetector` vão consumir `Posicao`/`IMURead` como
entrada, construindo a covariância a partir de `acuraciaHorizontalM` /
`acuraciaVelocidadeMps` — não de um valor arbitrado. Vale já deixar claro no
`docs/DECISIONS.md` que essa é a origem dos dados de incerteza.
