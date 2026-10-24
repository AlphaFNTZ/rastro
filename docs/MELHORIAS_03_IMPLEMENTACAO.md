# Melhorias 03 — Implementação e validação

Implementação da etapa inicial de [melhorias-aplicativo-03.md](../melhorias-aplicativo-03.md): repasse com orçamento pequeno e recuperação persistente de interrupções. Seleção inteligente de portadores, novos transportes, anexos e autenticação de conta continuam futuros.

## Comportamento entregue

- Mensagens novas possuem duas autorizações independentes de transporte, além da cópia própria do remetente.
- Portadores podem repassar mensagens e recibos para outros portadores sem cadastrar remetente, destinatário ou próximo portador como contatos pessoais.
- Remetente e destinatário continuam cadastrando um ao outro por QR Code. Chaves públicas recebidas pelo transporte não entram na agenda.
- A transferência fica registrada e bloqueada no cedente antes da emissão da delegação assinada. O receptor confirma somente após gravar.
- Perda de oferta, confirmação ou conexão não cria orçamento novo. O reencontro consulta a mesma transferência e recupera seu resultado persistente.
- Cada recibo tem duas autorizações próprias. Seu retorno pode usar um caminho diferente do caminho da mensagem.
- Somente a confirmação autenticada do destinatário marca a conversa como entregue. Armazenamento por um portador indica apenas transporte.
- Entrega direta, malha conectada e transporte persistente compartilham o envelope e a deduplicação. Retransmissão na malha conectada não concede custódia.
- Desativar o modo portador recusa novas responsabilidades de terceiros. As já aceitas continuam sendo tratadas.
- Remoção local preserva um marcador, não cancela cópias remotas e não devolve autorizações.

O aplicativo não depende de internet para transportar esses pacotes. A entrega ainda exige encontros/conexões viáveis antes da expiração; não há garantia de distância, prazo ou chegada.

## Organização

| Componente | Responsabilidade |
| --- | --- |
| `ForwardProtocol.kt` | Formato RCS2, política da origem, cadeia assinada, comandos de sessão e validação |
| `ForwardStore.kt` | Pacotes, autorizações, transferências duráveis, cotas, expiração e provas finais |
| `ForwardEngine.kt` | Negociação, consultas, delegações, retomada, entrega e retorno de confirmação |
| `ChatStore.kt` | Migração SQLite para versão 3 e preservação do recibo único no histórico |
| `ChatRuntime.kt` | Coordenação dos caminhos e validação final com os contatos pessoais |
| `CustodyStore.kt` | Fluxo legado e contabilização conjunta das cotas |
| `NearbyTransport.kt` | Reconhecimento de RCS2 no enlace existente |
| `ChatActivity` / `CustodyActivity` | Estados de transporte, autorizações locais e explicação da remoção |

SQL e criptografia permanecem no worker serial do chat. As Activities não decidem delegações nem orçamentos.

## Protocolo e persistência

### Autorização e sessão

A política assinada pela origem vincula ID/hash do envelope, origem, destino, duas autorizações, sete dias de validade máxima e dezesseis delegações por linha. Cada delegação inclui o ID da transferência, identidade pública do cedente, próximo responsável, orçamento temporal restante e vínculo com a cadeia anterior.

O receptor verifica as assinaturas, continuidade, destinatário da autorização, ausência de ciclos, IDs e limites. Cada conexão usa novos desafios; comandos assinados para outra sessão são rejeitados.

Inventários consultam disponibilidade antes de emitir a delegação. A primeira seleção é determinística entre pares elegíveis; tenta distribuir as duas linhas entre pares diferentes. Portadores aguardam pelo menos 60 segundos de residência antes de uma nova delegação, sem impedir entrega direta. Não há algoritmo de previsão de encontros nesta versão.

### Estados locais

| Estado interno | Significado |
| --- | --- |
| `ACTIVE` | Autorização disponível no responsável atual |
| `PENDING` | Delegação persistida; cedente bloqueado para outros pares |
| `TRANSFERRED` | Armazenamento confirmado pelo próximo responsável |
| `DONE` | Conclusão autenticada |
| `EXPIRED` | Orçamento temporal encerrado |
| `REMOVED` | Cópia removida localmente |

O pacote cifrado pode permanecer no cedente após o repasse para entrega direta ou tentativa limitada pela malha conectada; isso não reativa sua autorização. As duas linhas compartilham o conteúdo armazenado quando chegam ao mesmo aparelho.

Uma resposta perdida deixa a linha pendente para o mesmo par. Não existe liberação automática por timeout, recusa tardia, reinício ou remoção. Essa escolha pode reduzir a disponibilidade, mas evita gastar novamente um orçamento cuja aceitação é incerta.

### Recibos e limpeza

O destinatário grava mensagem recebida, ACK e política de retorno na mesma transação. A prova original fica vinculada à entrada do histórico: receber novamente a mensagem não cria outro recibo ou orçamento, inclusive depois da expiração operacional.

A política do ACK expõe uma prova assinada que vincula o ID/hash original ao envelope cifrado do recibo. Assim, intermediários podem encerrar o transporte da mensagem sem ler o texto. O remetente também decifra e confere o ACK antes de atualizar a conversa.

Provas finais são devolvidas em encontros/consultas, sem inundação ilimitada. Recibos têm prioridade na seleção da fila e espaço reservado. Um ACK válido que chegue tarde ainda pode confirmar o histórico expirado.

### Limites efetivos

| Item | Valor |
| --- | --- |
| Autorizações por mensagem nova | 2, além da cópia própria |
| Autorizações por recibo | 2, com orçamento independente |
| Validade operacional | Até 7 dias por mensagem; até 7 dias desde a criação do recibo |
| Delegações | Até 16 por linha, sem reinício do contador |
| Pacotes ativos | Até 128, somando fluxo novo e legado |
| Pacotes de terceiros por origem criptográfica | Até 8, somando fluxo novo e legado |
| Orçamento de transporte | 2 MiB, incluindo reservas |
| Admissão de texto | Até 1,5 MiB; os 512 KiB restantes ficam disponíveis para recibos |
| Reserva conservadora por pacote RCS2 ativo | 32 KiB |
| Marcadores | Até 2.048, somando os dois fluxos |
| Retenção após conclusão/remoção | 14 dias; controles repetidos não renovam o prazo |
| Expiração | Marcador preservado por 14 dias além do orçamento operacional inicial |
| Retentativa | Ciclo de 30 segundos; até 4 ações de fila por par/ciclo |
| Tentativas adicionais na malha conectada | Até 2 pacotes transportados por ciclo, sem novas autorizações |
| Pacote com provas | Até 14.000 bytes |
| Comando no enlace | Até 16.384 bytes |

A reserva de 32 KiB cobre conteúdo e crescimento das duas cadeias. Por isso uma fila exclusivamente RCS2 admite no máximo 48 pacotes de texto e mais 16 recibos antes de atingir o orçamento, mesmo que o limite nominal de 128 ainda não tenha sido alcançado. Pacotes legados e provas retidas também consomem capacidade. A interface explica essa reserva.

Esses limites são de conteúdo/reservas do transporte; não representam o tamanho total do arquivo SQLite, índices, histórico pessoal ou cache do mapa. Não se apagam marcadores necessários para admitir novas mensagens.

O prazo usa tempo monotônico no mesmo boot e tratamento conservador após reinício. Retrocesso de relógio não estende validade. Nenhum protocolo entre aparelhos sem relógio confiável consegue provar que um participante malicioso respeitou o tempo.

## Compatibilidade e dados existentes

A base passa da versão 2 para a 3 por migração aditiva: novas tabelas `forward_packets` e `forward_lanes`, mais `transport_version` e `forward_receipt` nas mensagens. A atualização desde a versão 1 também cria as tabelas do fluxo legado.

Contatos, identidade, histórico, preferências e custódias antigas são preservados. Mensagens antigas continuam com custódia RCS1 e entrega direta pelo portador. Elas não recebem políticas ou autorizações fabricadas durante a migração.

RCS2 negocia sua própria sessão. Pares antigos continuam nos fluxos que conhecem; mensagens novas não recebem custódia RCS1 como alternativa que duplicaria o orçamento. O envelope do chat continua compatível com entrega direta/malha conectada.

Nos testes Android 9 foi necessário declarar `ChatStore` explicitamente como `Closeable`, pois nessa plataforma o fechamento automático do helper não tinha a mesma compatibilidade das plataformas mais recentes. O QR Code também foi limitado à altura disponível para manter o botão de fechar acessível em telas pequenas.

## Interface e uso

1. Instale a versão atual nos participantes do ensaio.
2. Cadastre A e C mutuamente por QR Code.
3. Ative o Rastro nos aparelhos participantes.
4. Nos intermediários, habilite **Conversas privadas → Caixa de correspondência → Participar como portador**.
5. Envie uma mensagem de A para C. Intermediários podem continuar sem qualquer contato salvo.
6. Promova encontros separados e aguarde a residência mínima quando houver repasse para outro intermediário.
7. Proporcione um caminho de volta ao recibo até A.

Na conversa aparecem “Repasse em confirmação”, “Em transporte por portadores” e “Entrega confirmada”, conforme o estado verificável. A caixa mostra autorizações disponíveis/pendentes e validade local, sem afirmar trajeto físico, localização ou quantidade global de cópias.

As chaves públicas, identidades criptográficas, IDs, hashes, relações origem/destino e delegações são metadados visíveis ao transporte. O texto continua cifrado ponta a ponta. Redução desses metadados e revisão criptográfica especializada permanecem evoluções.

## Validação

### Build e testes locais

Comando executado:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

Resultado: **129 testes locais passaram**, sem falhas ou testes ignorados; build concluído. Lint: **0 erros e 117 avisos**. O relatório detalhado permanece em `app/build/reports/lint-results-debug.html`.

Os 16 cenários novos são compartilhados entre Robolectric e Android real/emulado. Cobrem cadeia de portadores sem contatos, retorno independente, perdas de oferta/confirmação, reinícios, duas linhas, desativação do modo portador, adulteração, sessão antiga, limite de saltos, ciclos, remoção, expiração, rollback, relógio, cotas, migração, entrega direta concorrente, retenção, recibos incompatíveis e recibos tardios.

Os testes de rollback e reinício cobrem transações e reabertura do estado; não simulam todas as possíveis falhas físicas do armazenamento.

### Três emuladores

Aparelhos: `emulator-5554`, `emulator-5556` e `emulator-5558`, Android 9/API 28.

A suíte de chat/custódia é reproduzível com:

```powershell
python scripts/test_chat_emulators.py
```

O script instala os APKs com `adb install -r`, preserva os dados do aplicativo e restaura as configurações temporárias de animações e banners ao final. Banners do Google Play Services estavam interceptando os toques de Espresso na região do cabeçalho; a suíte os desativa apenas durante o ensaio.

**Resultado final: 24 testes passaram em cada um dos três emuladores, totalizando 72 execuções sem falhas.** São 16 cenários novos de repasse e 8 testes de persistência, QR Code e interface de chat/custódia por aparelho. O APK atualizado ficou instalado nos três emuladores.

Os resultados finais dessa suíte ficam em `app/build/reports/chat-emulator-*.txt`.

### Troca controlada entre processos e aparelhos

```powershell
python scripts/test_forward_emulators.py
```

**Passou:** A → B → D → C; confirmação C → B → D → A.

- A e C são identidades/bancos isolados no emulador 5554; B usa o 5556 e D usa o 5558.
- Cada etapa abre um novo processo de instrumentação e recupera identidade e SQLite persistentes.
- B e D permanecem sem contatos pessoais.
- A confirmação de armazenamento de B é omitida intencionalmente; A continua pendente depois da reabertura.
- Repetir a oferta recupera a mesma autorização.
- Entrega repetida produz uma única mensagem e o mesmo recibo.
- O retorno termina com uma única mensagem no histórico de A e estado `DELIVERED`.
- Os bancos e preferências do laboratório são isolados e removidos ao final.

O host move os pacotes cifrados por ADB. Este ensaio valida persistência, criptografia e delegações entre instalações; **não valida o rádio Nearby**. O intervalo de residência é zerado apenas no laboratório para acelerar as etapas; a suíte compartilhada testa a política de produção com relógio controlado. O relatório sem pacotes/chaves fica em `app/build/reports/forward-emulators.json`.

### Limitações e ensaios pendentes

- Os emuladores não anunciam Bluetooth e não substituem dispositivos físicos para avaliar descoberta, interferência, alcance, bateria, tela bloqueada e encontros Nearby.
- A regressão instrumental de mapa/navegação encontrou `No Vulkan compatible GPU found`; o ambiente anuncia apenas OpenGL ES 2.0. Uma tentativa com backend OpenGL também falhou na renderização nativa. A dependência original do mapa foi preservada. A tela principal pode encerrar nesses emuladores por essa limitação anterior ao repasse.
- Testes locais de navegação passam; a validação física dos gestos informada anteriormente pelo usuário continua sendo evidência da versão anterior, não um novo ensaio de mapa.
- Compatibilidade lógica e migração são testadas. Falta ensaio de rádio com versões diferentes instaladas em aparelhos físicos.
- Próximo ensaio de campo: quatro aparelhos físicos A/B/D/C, encontros separados, origem indisponível durante B → D → C, retorno por caminho alternativo, encerramento/reabertura dos processos, bloqueio de tela e observação de energia.
- Não houve auditoria criptográfica independente, simulação exaustiva de participantes maliciosos ou medição de bateria. Assinaturas não impedem um portador de descartar a mensagem, clonar estado ou criar identidades.

## Artefatos

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- APK de testes: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- Testes locais: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Plano de referência: [melhorias-aplicativo-03.md](../melhorias-aplicativo-03.md).

A implementação inicial está disponível no código. A validação de campo do transporte físico permanece pendente; seleção inteligente e novos transportes continuam fora desta entrega.
