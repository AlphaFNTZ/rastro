# Sensibilidade da detecção à degradação injetada

Gerado por `EvidenciasAv1Test` (comando: `.\gradlew.bat :app:testDebugUnitTest --tests "*EvidenciasAv1Test" --rerun-tasks`).
Semente: `20261020`. Dados: `dados/movimento/gnss_20261022_155728.csv`.

30 janelas reais × 20 repetições por linha. α = 0.05 (Šidák por janela).
Menor salto com detecção ≥ 90%: 5 m.

| Degradação | Parâmetro | Salto (m) | Ensaios | Inviáveis | Detecção | Localização correta | Falso alarme |
|---|---|---|---|---|---|---|---|
| salto | 0.00 | 0 | 600 | 0 | — | — | 0% |
| salto | 2.00 | 2 | 600 | 0 | 6% | 6% | 2% |
| salto | 5.00 | 5 | 600 | 0 | 92% | 92% | 4% |
| salto | 10.00 | 10 | 600 | 0 | 98% | 98% | 2% |
| salto | 15.00 | 15 | 600 | 0 | 98% | 98% | 2% |
| salto | 20.00 | 20 | 600 | 0 | 100% | 100% | 2% |
| salto | 30.00 | 30 | 600 | 0 | 100% | 100% | 2% |
| salto | 50.00 | 50 | 600 | 0 | 100% | 100% | 2% |
| salto | 100.00 | 100 | 600 | 0 | 100% | 100% | 2% |
| ruído σ (m) | 0.00 | 20 | 600 | 0 | 100% | 100% | 2% |
| ruído σ (m) | 1.00 | 20 | 600 | 0 | 99% | 99% | 33% |
| ruído σ (m) | 2.00 | 20 | 600 | 0 | 99% | 97% | 94% |
| ruído σ (m) | 5.00 | 20 | 600 | 0 | 94% | 83% | 100% |
| ruído σ (m) | 10.00 | 20 | 600 | 0 | 62% | 37% | 100% |
| perda (prob.) | 0.00 | 20 | 600 | 0 | 100% | 100% | 2% |
| perda (prob.) | 0.10 | 20 | 600 | 20 | 100% | 100% | 1% |
| perda (prob.) | 0.20 | 20 | 600 | 47 | 100% | 100% | 1% |
| perda (prob.) | 0.30 | 20 | 600 | 83 | 100% | 100% | 1% |
| perda (prob.) | 0.50 | 20 | 600 | 249 | 100% | 100% | 0% |
