# Resíduo das restrições antes e depois da reconciliação — resumo

Gerado por `EvidenciasAv1Test` (comando: `.\gradlew.bat :app:testDebugUnitTest --tests "*EvidenciasAv1Test" --rerun-tasks`).
Semente: `20261020`. Dados: `dados/movimento/gnss_20261022_155728.csv`.

Janelas disjuntas de 10 fixos GPS contínuos; descartadas 5 de 90 com a posição retida.

| Grandeza (todas as janelas) | Valor |
|---|---|
| Janelas | 85 |
| RMS r antes (m): mínimo / mediana / máximo | 0.095 / 0.213 / 1.281 |
| RMS r depois (m): máximo | 2.0e-15 |
| γ/m médio (m = 2(N−1) graus de liberdade) | 0.24 |
| Janelas com γ acima do χ² de 95% | 0 |
| Janelas com fixo eliminado pelo detector | 1 |

As 3 primeiras janelas, na ordem do registro (todas no Apêndice B):

| Janela | Início (BRT) | Fixos | Duração (s) | RMS r antes (m) | máx \|r\| antes (m) | RMS r depois (m) | γ | χ² 95% | Ajuste médio posição (m) | Eliminados pelo detector |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 22/10/2026 15:57:32 | 10 | 9.0 | 1.281 | 4.160 | 2.0e-15 | 7.4 | 28.9 | 2.95 | nenhum |
| 2 | 22/10/2026 15:58:32 | 10 | 9.0 | 0.267 | 0.646 | 2.3e-16 | 11.1 | 28.9 | 0.45 | nenhum |
| 3 | 22/10/2026 15:58:42 | 10 | 9.0 | 0.246 | 0.434 | 8.5e-16 | 4.4 | 28.9 | 0.48 | nenhum |
