# AV1 — capacidades 1 a 5 no Rastro

Mapa de onde está cada capacidade no código, qual teste a verifica e qual arquivo
de `docs/evidencias/resultados` traz a evidência pedida na seção 10.3 do guia.
Roteiro de coleta e demonstração: [ENSAIO_AV1.md](ENSAIO_AV1.md). Decisões: D-17 a D-21
em [DECISIONS.md](DECISIONS.md). Relatório: [relatorio/RELATORIO_AV1.md](relatorio/RELATORIO_AV1.md).

| # | Capacidade | Código | Testes | Evidência |
|---|---|---|---|---|
| 1 | Domínio, contratos e estruturas | `Posicao`, `IMURead`, `Trajeto`, `DomainExceptions` (exceção selada própria), `av1/RegistroFix` | `PosicaoTest`, `IMUReadTest`, `TrajetoTest`, `RegistroCsvTest` | `testes_unidade.md` |
| 2 | Incerteza e reconciliação | `s2/CovarianciaGnss`, `av1/ModeloIncerteza`, `av1/Reconciliador`, `av1/DetectorErroGrosseiro`, `av1/Matriz` | `ReconciliadorTest`, `DetectorErroGrosseiroTest`, `MatrizTest`, `CovarianciaTest` | `estatico.md`, `dispersao_estatica.svg`, `residuos.md`, `demonstracao_detector.md` |
| 3 | Concorrência e região crítica | `s2/CircularImuBuffer` (monitor único; `snapshot`/`drenar` devolvem cópias) | `CircularImuBufferTest`, `CircularImuBufferConcurrencyTest` (10 rodadas) | `corrida_10_execucoes.md` |
| 4 | Aquisição periódica em tempo real | `service/RastroService` (serviço em primeiro plano `connectedDevice\|location`), `sensors/ImuSensorSource` (20 ms), `av1/GravadorAquisicao` (1 Hz, wake lock) | `AnalisesTest` (critério do trecho bloqueado) | `aquisicao.md` + demonstração ao vivo |
| 5 | Ensaio reprodutível e injeção de falhas | `av1/InjetorFalhas`, `av1/ReprodutorRegistro`, `av1/EnsaioSensibilidade`, `av1/EnsaioActivity` | `InjetorFalhasTest`, `EnsaioSensibilidadeTest`, `ReprodutorRegistroTest` | `sensibilidade.md` |

## Como rodar

```powershell
.\gradlew.bat :app:testDebugUnitTest                      # todos os testes de unidade
powershell -ExecutionPolicy Bypass -File scripts\av1_evidencias.ps1   # testes + 10 corridas + evidências
.\gradlew.bat :app:installDebug                           # instala no aparelho
```

`EvidenciasAv1Test` é pulado (não falha) enquanto não houver registros em
`docs/evidencias/dados`; depois de copiados, ele gera todas as tabelas com a semente 20261020.

## Threads da camada 1

| Thread | Quem cria | Período | O que faz |
|---|---|---|---|
| `rastro-imu-50hz` (HandlerThread) | `ImuSensorSource` | 20 ms solicitados ao `SensorManager` | Produtor: `CircularImuBuffer.adicionar` |
| `rastro-consumidor-imu` | `RastroService` | 1 s (`scheduleWithFixedDelay`) | Consumidor: `drenar`, métricas, entrega o lote ao gravador |
| `rastro-registro-1hz` | `GravadorAquisicao` | 1 s (`scheduleAtFixedRate`) | Linha do log de aquisição (bateria, tela, IMU, fixos) |
| principal (Looper) | Android | 1 s (`requestLocationUpdates`) | Fixo GNSS → `GravadorAquisicao.registrarFix` |
| `rastro-reproducao` | `EnsaioActivity` | Δt real ÷ aceleração | Reprodução, reconciliação e detecção (fora da thread de interface) |

Regiões críticas: o monitor do `CircularImuBuffer` (índices, slots e contadores) e o monitor do
`GravadorAquisicao` (escritores e contadores). O buffer não faz E/S com o monitor tomado. O
gravador escreve uma linha de ~150 bytes no armazenamento local com o monitor tomado (para a
linha não sair intercalada); bateria, tela e bloqueio são lidos antes de entrar.
