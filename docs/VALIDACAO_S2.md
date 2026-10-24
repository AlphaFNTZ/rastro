# Evidências de validação — S2

Data: 22/09/2026. Validação automatizada nesta máquina, sem aparelhos conectados.

## Núcleo JVM

Os fontes de domínio, S2, protocolo, transporte TCP e todos os testes de
`app/src/test/java` foram compilados com Kotlin 2.3.21, alvo JVM 11, e executados
com JUnit 4.13.2 sobre JDK 25.0.4.1. Resultado: **59 testes, zero falhas**.
Inclui os 34 testes de domínio S1, o teste de exemplo, 6 de covariância/IMU,
4 de buffer, **10 rodadas concorrentes**, 3 de protocolo e 1 de TCP local.

```text
JUnit version 4.13.2
...........................................................
Time: 0,284
OK (59 tests)
```

A execução direta na JVM foi feita enquanto o SDK Android era preparado e não
substitui a compilação do APK. Usa os mesmos fontes de produção e testes do módulo,
sem stubs de domínio ou implementação alternativa do buffer.

## Android

Build, lint e testes via Gradle: em verificação; atualizar com o resultado final.
O ambiente inicial não tinha Java nem SDK configurados. Para esta validação foram
baixados JDK e SDK oficiais para pastas temporárias, com checksum verificado nos
arquivos de distribuição. Não foram alteradas as versões do projeto.

## Pendências de evidência física e revisão

- Testes instrumentados `LocationMapperTest`: não executados em dispositivo.
- Descoberta, conexão, reconexão e SOS/ACK por Wi-Fi Direct: ensaio físico pendente.
- Frequência IMU e acurácia GNSS reais: coleta física pendente.
- Multi-hop: não implementado nem validado. Loopback TCP não é prova de mesh.
- Fonte quantitativa de incerteza IMU: a API padrão não a informa; alinhar alternativa
  com o docente antes de declarar esse critério cumprido.
- PR remoto e revisão pelo parceiro: pendentes. Descrição preparada em `PR_SEMANA2.md`.

Preencher os resultados físicos conforme o roteiro de `README_Semana2.md`, sem
converter ausência de medição em sucesso ou em valores numéricos inventados.
