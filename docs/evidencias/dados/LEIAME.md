# Registros reais da AV1

Gravados pelo aplicativo (`GravadorAquisicao`) e copiados do celular por USB. Cada sessão tem dois
arquivos com o mesmo carimbo: `gnss_…csv` (um fixo por linha) e `aquisicao_…csv` (uma linha por
segundo). Colunas em `RegistroFix.kt`.

| Pasta | Sessão | O que é |
|---|---|---|
| `bloqueado/` e `estatico/` | `20261022_211230` | 36 min parado e com a tela bloqueada, depois da correção D-23 (localização em segundo plano). Gravada fora do campus: **coordenadas deslocadas** por um vetor constante (D-25). |
| `bloqueado_antes_d23/` | `20261022_152023` | 36 min parado e bloqueado, no campus, **antes** da D-23: mostra o GPS cortado ~3 min depois de a tela apagar. |
| `movimento/` | `20261022_155728` | Caminhada de 15 min no campus, com a tela ligada. |

O `EvidenciasAv1Test` lê estas pastas e grava as tabelas em `../resultados`.
