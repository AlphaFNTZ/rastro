# Verificação da implementação com dados SINTÉTICOS

> Não é evidência física. Caminhada simulada (`Sintetico.caminhada`, teste de unidade) com r68 = 4 m, 1,4 m/s,
> σ da velocidade 0,2 m/s, σ do rumo 5°, fixos a 1 s, e ruído coerente com a acurácia declarada.
> Serve para mostrar que a implementação se comporta como a teoria prevê antes de usar os dados reais.
> Semente 20261020; 20 janelas de 10 fixos × 20 repetições por linha.

Consistência estatística: média de γ/m em 300 janelas limpas = 0.983 (esperado 1, pois γ ~ χ²(m)).

## Sensibilidade

| Degradação | Parâmetro | Salto (m) | Ensaios | Inviáveis | Detecção | Localização correta | Falso alarme |
|---|---|---|---|---|---|---|---|
| salto | 0.00 | 0 | 400 | 0 | — | — | 0% |
| salto | 2.00 | 2 | 400 | 0 | 0% | 0% | 0% |
| salto | 5.00 | 5 | 400 | 0 | 5% | 5% | 4% |
| salto | 10.00 | 10 | 400 | 0 | 54% | 51% | 5% |
| salto | 15.00 | 15 | 400 | 0 | 96% | 96% | 1% |
| salto | 20.00 | 20 | 400 | 0 | 100% | 100% | 1% |
| salto | 30.00 | 30 | 400 | 0 | 100% | 100% | 0% |
| salto | 50.00 | 50 | 400 | 0 | 100% | 100% | 0% |
| salto | 100.00 | 100 | 400 | 0 | 100% | 100% | 0% |
| ruído σ (m) | 0.00 | 20 | 400 | 0 | 100% | 100% | 1% |
| ruído σ (m) | 1.00 | 20 | 400 | 0 | 100% | 99% | 4% |
| ruído σ (m) | 2.00 | 20 | 400 | 0 | 100% | 98% | 21% |
| ruído σ (m) | 5.00 | 20 | 400 | 0 | 90% | 78% | 95% |
| ruído σ (m) | 10.00 | 20 | 400 | 0 | 57% | 32% | 100% |
| perda (prob.) | 0.00 | 20 | 400 | 0 | 100% | 100% | 1% |
| perda (prob.) | 0.10 | 20 | 400 | 13 | 100% | 100% | 1% |
| perda (prob.) | 0.20 | 20 | 400 | 32 | 100% | 99% | 1% |
| perda (prob.) | 0.30 | 20 | 400 | 53 | 100% | 100% | 1% |
| perda (prob.) | 0.50 | 20 | 400 | 161 | 100% | 100% | 0% |

Menor salto com detecção ≥ 90%: 15.0 m.

## Resíduo antes e depois (3 janelas)

| Janela | Início (BRT) | Fixos | Duração (s) | RMS r antes (m) | máx \|r\| antes (m) | RMS r depois (m) | γ | χ² 95% | Ajuste médio posição (m) | Eliminados pelo detector |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | — | 10 | 9.0 | 2.764 | 4.826 | 2.2e-15 | 11.1 | 28.9 | 2.61 | nenhum |
| 2 | — | 10 | 9.0 | 3.438 | 6.514 | 6.3e-15 | 19.8 | 28.9 | 3.39 | nenhum |
| 3 | — | 10 | 9.0 | 4.433 | 8.730 | 3.6e-15 | 24.3 | 28.9 | 3.91 | nenhum |

## Sem o detector a reconciliação piora (salto de 30 m)

| Janela | Fixo com salto | Salto (m) | RMS vizinhos SEM detector (m) | RMS vizinhos COM detector (m) | Erro no fixo sem detector (m) | Detectou | Localizou |
|---|---|---|---|---|---|---|---|
| 1 | 5 | 30 | 2.99 | 0.44 | 3.07 | sim | sim |
| 2 | 5 | 30 | 2.99 | 0.35 | 3.05 | sim | sim |
| 3 | 5 | 30 | 2.99 | 0.52 | 3.05 | sim | sim |
| 4 | 5 | 30 | 2.99 | 0.53 | 3.08 | sim | sim |
| 5 | 5 | 30 | 2.99 | 0.34 | 3.10 | sim | sim |

## Ensaio estático: sensor calibrado × sensor otimista (ruído 1,6 × o declarado)

| | Calibrado | Otimista |
|---|---|---|
| σ declarado por eixo (m) | 3.31 | 3.31 |
| σ medido (leste / norte, m) | 3.32 / 3.24 | 5.27 / 5.26 |
| Razão de otimismo | 0.99 | 1.59 |
| Fração dentro do próprio r68 | 69% | 37% |
