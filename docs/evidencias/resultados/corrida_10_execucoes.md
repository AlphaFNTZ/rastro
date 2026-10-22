# Teste de corrida — dez execuções, uma linha por execução

- Comando (repetido 10x): `.\gradlew.bat :app:testDebugUnitTest --tests "*CircularImuBufferConcurrencyTest" --rerun-tasks`
- Cada execução roda as 10 rodadas parametrizadas (produtor, consumidor e observador concorrentes, 10 000 leituras em buffer de 64). Commit `b9c70d3` (com alterações não comitadas), CORSA / Microsoft Windows NT 10.0.26100.0.

| Execução | Início | Casos | Falhas | Tempo JUnit (s) | Tempo total (s) | Código | Resultado |
|---|---|---|---|---|---|---|---|
| 1 | 21/10/2026 20:38:57 | 10 | 0 | 0.077 | 16 | 0 | PASSOU |
| 2 | 21/10/2026 20:39:13 | 10 | 0 | 0.084 | 14.1 | 0 | PASSOU |
| 3 | 21/10/2026 20:39:27 | 10 | 0 | 0.08 | 13.1 | 0 | PASSOU |
| 4 | 21/10/2026 20:39:40 | 10 | 0 | 0.079 | 11.5 | 0 | PASSOU |
| 5 | 21/10/2026 20:39:52 | 10 | 0 | 0.103 | 11.4 | 0 | PASSOU |
| 6 | 21/10/2026 20:40:03 | 10 | 0 | 0.076 | 11.2 | 0 | PASSOU |
| 7 | 21/10/2026 20:40:15 | 10 | 0 | 0.079 | 10.7 | 0 | PASSOU |
| 8 | 21/10/2026 20:40:25 | 10 | 0 | 0.073 | 11 | 0 | PASSOU |
| 9 | 21/10/2026 20:40:36 | 10 | 0 | 0.076 | 10.9 | 0 | PASSOU |
| 10 | 21/10/2026 20:40:47 | 10 | 0 | 0.079 | 10.5 | 0 | PASSOU |
