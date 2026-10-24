# Sem o detector, a reconciliação piora — resumo

Gerado por `EvidenciasAv1Test` (comando: `.\gradlew.bat :app:testDebugUnitTest --tests "*EvidenciasAv1Test" --rerun-tasks`).
Semente: `20261020`. Dados: `dados/movimento/gnss_20261022_155728.csv`.

Salto de 30 m injetado no fixo central de cada janela; referência = reconciliação da janela limpa.

| Grandeza (todas as janelas) | Valor |
|---|---|
| Janelas | 85 |
| RMS dos vizinhos SEM detector (m), média | 2.94 |
| RMS dos vizinhos COM detector (m), média | 0.07 |
| Salto detectado / localizado no fixo certo | 85 / 85 de 85 |

As 3 primeiras janelas, na ordem do registro (todas no Apêndice B):

| Janela | Fixo com salto | Salto (m) | RMS vizinhos SEM detector (m) | RMS vizinhos COM detector (m) | Erro no fixo sem detector (m) | Detectou | Localizou |
|---|---|---|---|---|---|---|---|
| 1 | 5 | 30 | 0.10 | 0.01 | 0.10 | sim | sim |
| 2 | 5 | 30 | 4.95 | 0.11 | 5.71 | sim | sim |
| 3 | 5 | 30 | 2.18 | 0.05 | 2.51 | sim | sim |
