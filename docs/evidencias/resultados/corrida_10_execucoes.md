# Teste de corrida — dez execuções, uma linha por execução

- Comando (repetido 10x): `.\gradlew.bat :app:testDebugUnitTest --tests "*CircularImuBufferConcurrencyTest" --rerun-tasks`
- Cada execução roda as 10 rodadas parametrizadas (produtor, consumidor e observador concorrentes, 10 000 leituras em buffer de 64). Commit `710ef50` (árvore limpa), JOAO-PAULO / Microsoft Windows NT 10.0.26300.0.

| Execução | Início | Casos | Falhas | Tempo JUnit (s) | Tempo total (s) | Código | Resultado |
|---|---|---|---|---|---|---|---|
| 1 | 22/10/2026 23:27:24 | 10 | 0 | 0.059 | 6.8 | 0 | PASSOU |
| 2 | 22/10/2026 23:27:30 | 10 | 0 | 0.053 | 8.8 | 0 | PASSOU |
| 3 | 22/10/2026 23:27:39 | 10 | 0 | 0.059 | 8.9 | 0 | PASSOU |
| 4 | 22/10/2026 23:27:48 | 10 | 0 | 0.061 | 8.2 | 0 | PASSOU |
| 5 | 22/10/2026 23:27:56 | 10 | 0 | 0.059 | 8.8 | 0 | PASSOU |
| 6 | 22/10/2026 23:28:05 | 10 | 0 | 0.055 | 8.3 | 0 | PASSOU |
| 7 | 22/10/2026 23:28:14 | 10 | 0 | 0.058 | 8.6 | 0 | PASSOU |
| 8 | 22/10/2026 23:28:22 | 10 | 0 | 0.055 | 8.6 | 0 | PASSOU |
| 9 | 22/10/2026 23:28:31 | 10 | 0 | 0.059 | 8.3 | 0 | PASSOU |
| 10 | 22/10/2026 23:28:39 | 10 | 0 | 0.055 | 8.3 | 0 | PASSOU |
