# Roteiro de coleta e de demonstração — AV1

Tudo o que a AV1 pede de evidência física sai de **duas sessões de gravação** no celular
e de **um script** no computador. Nenhum número do relatório é digitado à mão.

## 0. Preparar o aparelho (uma vez)

1. Instale o APK desta versão: `.\gradlew.bat :app:installDebug`.
2. Configure um bloqueio de tela (PIN, padrão ou senha). Sem ele o Android nunca informa
   "bloqueado" e o log só registra a tela apagada.
3. Em Configurações do Android → Apps → Rastro → Bateria, escolha **Sem restrições**.
   Xiaomi, Samsung e Motorola matam serviços em segundo plano sem isso.
4. Abra o Rastro, toque em **Ativar** e conceda: dispositivos próximos, localização
   **precisa** e notificações.
5. Confira na tela principal a segunda linha do status:
   `Fixos GNSS 12 · IMU 600 (50 Hz) · 00:00:12`. Se aparecer, a gravação está ativa.

Os arquivos ficam em `Android/data/com.example.rastro/files/registros`, um par por sessão:
`gnss_AAAAMMDD_HHMMSS.csv` e `aquisicao_AAAAMMDD_HHMMSS.csv` (mesmo carimbo).
Cada **Ativar** abre uma sessão nova; **Desativar** fecha os arquivos.

> Os registros contêm posição real e o repositório é público no GitHub. Grave no campus,
> não em casa.

## 1. Sessão A — parado e bloqueado (capacidades 2 e 4)

Serve para duas evidências ao mesmo tempo: o log de 30 minutos com o aparelho bloqueado e
o gráfico de dispersão das leituras estáticas.

1. Vá para um lugar **a céu aberto**, longe de prédios e árvores (gramado, estacionamento).
2. Coloque o celular **parado** sobre uma superfície, tela para cima.
3. Toque em **Ativar**, espere 1 minuto (o GNSS converge) e **bloqueie a tela**.
4. Não toque no aparelho por **pelo menos 35 minutos**.
5. Desbloqueie, toque em **Desativar**.

## 2. Sessão B — caminhada (capacidades 2 e 5)

1. A céu aberto, toque em **Ativar** e espere 1 minuto parado.
2. Caminhe **10 a 15 minutos** em ritmo normal, com o celular na mão, incluindo retas e
   algumas curvas. Evite túneis, marquises e passar por dentro de prédios.
3. Toque em **Desativar**.

Com fixos a 1 s, 10 minutos dão ~60 janelas de 10 fixos. O mínimo exigido são 3.

## 3. Copiar para o repositório

Com o celular no USB e a depuração autorizada (veja a lista com `-Listar`):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\av1_copiar_registros.ps1 -Listar
powershell -ExecutionPolicy Bypass -File scripts\av1_copiar_registros.ps1 -Ensaio bloqueado -Sessao <carimbo da sessão A>
powershell -ExecutionPolicy Bypass -File scripts\av1_copiar_registros.ps1 -Ensaio estatico  -Sessao <carimbo da sessão A>
powershell -ExecutionPolicy Bypass -File scripts\av1_copiar_registros.ps1 -Ensaio movimento -Sessao <carimbo da sessão B>
```

Sem adb, copie pelo Explorador de Arquivos para as mesmas pastas:
`docs\evidencias\dados\bloqueado`, `...\estatico` e `...\movimento`.

## 4. Gerar as evidências

```powershell
powershell -ExecutionPolicy Bypass -File scripts\av1_evidencias.ps1
```

O script roda a suíte de testes, o teste de corrida dez vezes e o `EvidenciasAv1Test`,
que lê os registros e grava em `docs\evidencias\resultados`:

| Arquivo | Evidência (seção 10.3 do guia) | Capacidade |
|---|---|---|
| `testes_unidade.md` | Saída do comando de teste | todas |
| `corrida_10_execucoes.md` | Tabela com dez execuções, uma linha por execução | 3 |
| `aquisicao.md` | Log de ≥ 30 min com o aparelho bloqueado; linha "Critério" | 4 |
| `estatico.md`, `dispersao_estatica.svg` | Dispersão estática e razão de otimismo | 2 |
| `residuos.md` | Resíduo antes e depois em ≥ 3 janelas reais | 2 |
| `demonstracao_detector.md` | Sem o detector, a reconciliação piora | 2 |
| `sensibilidade.md` | Sensibilidade da detecção à degradação, com a semente | 5 |

Confira a linha **Critério** de `aquisicao.md`. Se der "NÃO ATENDIDO", veja a maior lacuna
e a taxa mínima da IMU: normalmente é otimização de bateria do fabricante (passo 0.3).

## 5. Demonstração ao vivo (MVP, 9 min)

O guia (seção 10.4) pede na tela: aquisição com o aparelho bloqueado e contagem de fixos
crescendo; reprodução acelerada com injeção de falha; resíduo antes e depois com números;
erro grosseiro detectado.

1. **Antes da aula:** carregue o celular, toque em **Ativar** 30+ minutos antes e bloqueie.
2. **Aparelho bloqueado:** aperte o botão liga/desliga sem desbloquear. A notificação do
   Rastro mostra `Fixos GNSS … · IMU … · hh:mm:ss` subindo. (Se a tela de bloqueio esconder
   notificações silenciosas, ative "mostrar notificações silenciosas" nas configurações.)
3. **Desbloqueie** e dê **toque longo no status** da tela principal → abre *Ensaio AV1*
   (também em Configurações → Diagnóstico → Abrir ensaio AV1). O bloco 1 mostra a aquisição ao vivo.
4. Em **Escolher registro GNSS**, selecione a caminhada. Salto = `30`, aceleração = `10`,
   semente = `20261020` → **Reproduzir**. O bloco 3 mostra, a cada fixo, o resíduo RMS antes
   e depois, γ contra o limiar χ², e "ERRO GROSSEIRO no fixo N ← o salto injetado".
5. **Falha de propósito (nível Excelente):** repita com um salto abaixo do mínimo detectável
   de `sensibilidade.md` (por exemplo `5`) e mostre que ele passa sem ser detectado. Esse é o
   comportamento no limite, e a tabela explica por quê.
6. **Comparar com e sem detector:** mostra, por janela, quanto os fixos vizinhos se afastam
   da referência sem o detector e com ele.

Gravação de tela só como contingência declarada (limita o item ao nível Adequado).
