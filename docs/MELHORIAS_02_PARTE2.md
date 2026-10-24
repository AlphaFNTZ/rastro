# Melhorias 02 — Parte 2: portadores com entrega direta

Data: 30/09/2026. Plano: [melhorias-aplicativo-02.md](../melhorias-aplicativo-02.md).
Base: [chat privado da Parte 1](MELHORIAS_02_PARTE1.md).

## Implementação

O remetente mantém sua mensagem e atribui no máximo um portador cooperativo por mensagem. Esse portador grava o envelope cifrado, entrega exclusivamente ao destinatário diretamente conectado e guarda o recibo para devolvê-lo à origem em outro encontro. Não há repasse de custódia entre portadores.

Foram mantidos Kotlin, SQLite, Tink, Nearby e o serviço existente. O banco passa da versão 1 para a 2 com migração aditiva; contatos, identidade e histórico são preservados.

## Como usar

1. Atualize todos os aparelhos para esta versão.
2. A e C devem cadastrar um ao outro por QR Code em **Dispositivos → Conversas privadas**.
3. No aparelho B, abra **Conversas privadas → Caixa de correspondência** e habilite **Participar como portador**. B não precisa cadastrar A ou C como contato.
4. Ative o Rastro nos aparelhos envolvidos, com as permissões necessárias.
5. A envia uma mensagem a C e encontra B. O estado passa por **Custódia em confirmação** e **Copiada para portador**.
6. A pode se desconectar. B mantém o pacote após fechar o aplicativo ou reiniciar. Reative o Rastro quando necessário para retomar a descoberta.
7. B encontra C diretamente. C valida e salva a mensagem; B guarda a confirmação cifrada.
8. B encontra A e devolve a confirmação. Somente o recibo autenticado de C permite mostrar **Entrega confirmada**.

A cópia própria de A continua elegível para entrega direta ou pela malha conectada da Parte 1. Entregas repetidas não duplicam a conversa.

O modo portador começa desativado. Desativá-lo impede novas custódias, mas mantém as entregas e os recibos já aceitos. A tela mostra pacotes, bytes de conteúdo e validade restante. **Remover cópia local** exige confirmação e descarta somente a mensagem/recibo transportado por aquele aparelho; pode impedir sua entrega. O histórico próprio não é removido.

## Protocolo e identidade dos encontros

CustodyProtocol.kt define o protocolo binário RCS1, separado do RCH1 do chat e do SOS v3. Comandos de custódia usam somente envio ao endpoint conectado; nunca usam o flooding do chat.

Cada conexão troca desafios novos e uma apresentação assinada com as chaves públicas e a capacidade de aceitar custódias. Controles assinados ficam vinculados ao desafio do receptor. O nome ou UUID do Nearby não define a identidade criptográfica. Essa prova comprova posse da chave no protocolo; não autentica civilmente a pessoa, não substitui o cadastro mútuo do chat e não promete resistência a retransmissores maliciosos de rádio.

A sincronização usa consultas limitadas de ID/hash/tamanho antes de oferecer o pacote. Somente a origem autenticada pode oferecer custódia de seu envelope. Pacotes já gravados respondem com seu estado ou recibo, sem nova cópia.

A atribuição ao portador é persistida antes da oferta. A aceitação só é respondida após a transação de gravação. Se uma resposta se perde, as consultas continuam com o mesmo portador, inclusive após reinício. Recusa, descarte ou indisponibilidade não provocam substituição automática. Isso pode deixar a atribuição pendente até expirar; a política evita distribuir uma segunda custódia quando a primeira é incerta.

O destinatário salva mensagem e recibo numa transação. O recibo leva ID e hash do envelope original e permanece protegido pela criptografia da Parte 1. A origem verifica autoria, destino e referência antes de confirmar entrega. B mantém o recibo até a origem confirmar seu salvamento ou até expirar. Se A já recebeu o ACK por outro caminho, solicita liberação da cópia em B no reencontro.

## Persistência e limites

Novas tabelas:

- dispatch: portador atribuído à mensagem própria, orçamento de validade e liberação.
- custody: envelope cifrado de terceiros, recibo, estado, validade e tentativa.
- custody_settings: preferência local de participação.

O portador não recebe chaves privadas nem texto aberto. Identidades, tamanho, estado e outros metadados de roteamento permanecem visíveis.

| Item | Limite |
| --- | --- |
| Validade operacional | 7 dias de orçamento restante |
| Pacotes ativos por portador | 128 |
| Pacotes ativos por origem | 8 |
| Conteúdo e reserva para recibos | 2 MiB |
| Reserva por recibo ainda não recebido | 12 KiB |
| Registros de custódia, incluindo marcadores | 2.048 |
| Envelope aceito pelo protocolo de custódia | 12 KiB |
| Moldura de controle | 16 KiB |
| Pares autenticados simultâneos | 32 |
| Deduplicação de controles | 512 IDs por sessão |
| Lote de pacotes/recibos por par | Até 4 por sincronização |
| Retentativa periódica | 30 segundos enquanto conectado |

A tela mostra bytes realmente ocupados por envelopes/recibos, sem somar reservas. As cotas não representam o tamanho físico exato do arquivo SQLite. A reserva pode impedir novas custódias antes de o contador visual chegar a 2 MiB. As cotas de histórico, texto e fila própria da Parte 1 continuam aplicáveis; mensagens expiradas deixam de ocupar a cota de saída pendente.

A validade usa tempo monotônico durante o mesmo boot. Entre boots, desconta o avanço do relógio civil; recuo detectado expira conservadoramente. Não existe relógio universal confiável nem garantia de validade contra manipulação deliberada do aparelho.

Uma oferta repetida não renova o prazo de uma custódia conhecida. Ao expirar, o conteúdo transportado é eliminado; marcadores de deduplicação são conservados por mais sete dias do orçamento original e depois removidos. Remoção manual e conclusão também conservam esses marcadores. O histórico e os recibos próprios permanecem na política de retenção da Parte 1.

Mensagens próprias anteriores à atualização recebem orçamento de sete dias quando a manutenção as encontra pela primeira vez. A expiração é aplicada na manutenção da fila/sincronização e ao consultar a caixa, sem trabalho permanente enquanto o serviço está parado. **Expirada sem confirmação** não prova que C nunca recebeu: um recibo autenticado tardio ainda pode confirmar o histórico.

A fila dedicada de chat e seu limite de admissão permanecem separados do processamento de SOS. Isso limita a carga, mas o rádio continua compartilhado e não oferece garantia de prioridade de tempo real.

## Arquivos principais

Caminhos relativos a app/src/main/java/com/example/rastro:

- chat/CustodyProtocol.kt: parser, controles e orçamento temporal.
- chat/CustodyStore.kt: armazenamento, cotas, atribuição, recibos e expiração.
- chat/CustodyEngine.kt: prova de identidade e sincronização direta.
- chat/ChatRuntime.kt: integração com a fila e validação final de mensagens/ACK.
- chat/ChatStore.kt: migração v1 → v2 e estados da saída.
- CustodyActivity.kt: participação, consumo e remoção confirmada.
- network/NearbyTransport.kt e service/RastroService.kt: endpoints e envio direto.

## Validação

Executado em 30/09/2026:

- **109 testes locais aprovados, zero falhas**, incluindo 11 novos testes de custódia.
- APK do aplicativo e APK de testes instrumentados compilados.
- Lint: **zero erros, 115 avisos** no projeto completo.
- **16 testes instrumentados aprovados** no Samsung SM-S926B, Android 16/API 36, via ADB.
- Atualização instalada com preservação de dados; aplicativo mantido instalado após os testes.

Os testes locais executam criptografia real e SQLite com Robolectric/API 28, simulando encontros separados A–B, B–D, B–C e B–A. Cobrem reinício/reabertura, ausência de repasse a D, perda de confirmação de custódia, perda de recibo, entrega anterior direta, perda de confirmação final, modo desativado, sessão antiga, assinatura inválida, cotas, reservas, remoção, expiração e migração.

CustodyPersistenceTest verifica no Android real o armazenamento cifrado e a reabertura de pacotes/recibos, além da migração preservando contatos e histórico. CustodyActivityTest verifica navegação e recriação da tela. Os testes de dados usam bancos isolados; a navegação preserva preferência e pacotes do usuário.

Também passaram ChatPersistenceTest, ChatActivityTest, ObservadoresTelaTest, MapaActivityTest, LocationMapperTest, PosicaoMapaTest e TelasDesignTest. Não foi executado o teste que depende de baixar mapa offline.

Comando local:

    .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

Para instalar e testar preservando dados do aplicativo:

    adb install -r app/build/outputs/apk/debug/app-debug.apk
    adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb shell am instrument -w -e class com.example.rastro.CustodyPersistenceTest,com.example.rastro.CustodyActivityTest com.example.rastro.test/androidx.test.runner.AndroidJUnitRunner

A opção connectedDebugAndroidTest também está disponível, mas o executor Gradle pode desinstalar os APKs ao terminar; use dispositivo de teste quando for escolhê-la.

Ainda requer ensaio com três ou quatro aparelhos: encontros físicos separados, comportamento dos rádios em tela bloqueada, recuperação após reinício completo do aparelho, restrições de bateria e consumo. A simulação não equivale a validação de alcance ou entrega garantida.

## Evoluções futuras

Continuam fora desta entrega: repasse entre portadores, múltiplas cópias com orçamento, substituição de portador, seleção por histórico de encontros, confirmação de leitura, anexos, grupos e autenticação de conta. As propostas completas permanecem na seção de evoluções do plano.

A entrega depende dos encontros e da cooperação dos aparelhos. Um portador pode perder ou descartar pacotes; limites de cópias organizam o comportamento do software cooperativo e não impedem um aparelho malicioso de copiar bytes.
