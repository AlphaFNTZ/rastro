# Melhorias 02 — Parte 1: chat privado

Data: 30/09/2026. Plano: [melhorias-aplicativo-02.md](../melhorias-aplicativo-02.md).

## O que foi implementado

- Identidade criptográfica persistente, distinta do UUID público do Nearby.
- Cadastro mútuo de contatos por QR Code, com confirmação presencial, impressão digital da identidade e apelido local.
- Chat de texto direto ou pela malha simultaneamente conectada.
- Criptografia ponta a ponta, autenticação do remetente e recibo autenticado de entrega.
- SQLite com contatos, histórico, caixa de saída, deduplicação e recibos recebidos pelo destinatário.
- Texto, QR cadastrado e apelido protegidos em repouso por AES-GCM com chave do Android Keystore.
- Retentativa de mensagens próprias enquanto o Rastro estiver ativo e houver enlaces.
- Estados de espera, tentativa e entrega confirmada. Recriação da Activity não apaga o histórico persistido.

Na entrega original da Parte 1, a Parte 2 ainda não estava implementada. Consulte agora a [implementação da Parte 2](MELHORIAS_02_PARTE2.md), que amplia o comportamento descrito neste registro histórico. Um retransmissor não grava pacotes de terceiros para encontros futuros. A fila persistente desta entrega pertence somente ao remetente e ao destinatário.

## Como usar

1. Instale a mesma versão do APK nos aparelhos envolvidos.
2. Abra **Dispositivos → Conversas privadas**.
3. No aparelho A, toque em **Meu QR Code**. No aparelho C, toque em **Ler QR Code**, autorize a câmera e confirme presencialmente o contato.
4. Repita no sentido inverso: A também precisa ler e cadastrar C.
5. Volte à tela inicial e ative o Rastro nos aparelhos que participarão da comunicação, concedendo as permissões solicitadas.
6. Abra a conversa pelo contato e envie texto.
7. Para editar o apelido e consultar a identidade, mantenha pressionado o contato na lista.

O QR pode ser cadastrado sem internet ou conexão Nearby. Se não houver câmera/permissão, o aplicativo explica a limitação; mostrar o próprio QR e consultar o histórico continuam possíveis.

O estado **Entrega confirmada** significa que o aplicativo destinatário validou e gravou a mensagem, não que uma pessoa a leu. O sucesso de envio pelo Nearby não altera sozinho esse estado.

Mensagem sem caminho permanece salva. Um destinatário que ainda não cadastrou o remetente rejeita o pacote; depois do cadastro mútuo, novas tentativas podem concluir a entrega.

## Identidade e criptografia

Dependências fixadas no catálogo Gradle:

- Tink Android 1.18.0.
- ZXing Android Embedded 4.3.0.
- Robolectric 4.17 apenas para testes locais.

Cada instalação gera dois keysets Tink: HPKE X25519/HKDF-SHA256/AES-256-GCM para confidencialidade e ECDSA P-256 para assinatura. Os keysets privados são serializados com a API de keyset cifrado do Tink, usando AEAD apoiada em chave AES-GCM do Android Keystore. Não existe fallback para armazenamento de chave em texto aberto.

O ID de chat é SHA-256 da representação binária, delimitada por comprimentos, dos dois keysets públicos. O QR v1 leva somente nome e esses keysets públicos. Nome e apelido não alteram o ID. Uma chave diferente resulta em outro contato; nunca substitui silenciosamente o contato anterior, mesmo se os nomes forem iguais.

O cabeçalho imutável do envelope contém versão, ID da mensagem, origem, destino, tipo e horário. Ele é contexto autenticado do HPKE. A assinatura cobre esse cabeçalho e o conteúdo cifrado. O destinatário verifica a assinatura contra a chave previamente cadastrada antes de decifrar.

O ACK também é cifrado e assinado; seu conteúdo referencia o UUID e o hash do envelope original. Um ACK de outro contato, de outra mensagem ou com conteúdo alterado não confirma a saída.

Os keysets aceitos pelo QR são públicos, de uma única chave e das famílias HPKE/ECDSA previstas nesta versão. A privacidade inicial depende da verificação presencial do QR apresentado no aparelho correto. O rádio Nearby continua aceitando conexões automaticamente, mas essa aceitação não cadastra contatos nem dá acesso ao texto privado.

Não há alegação de equivalência com Signal/WhatsApp, sigilo futuro ou revisão criptográfica externa. Os testes cobrem propriedades funcionais, não substituem auditoria do desenho completo.

## Protocolo e roteamento

O chat usa o marcador binário `RCH1` e envelope `rastro-chat-envelope-1`, separados do protocolo v3 de SOS. As mensagens legadas continuam no decodificador original. Não existe fallback para texto aberto quando o par não entende o novo protocolo.

A moldura de transporte contém um UUID de tentativa e um limite inicial de oito encaminhamentos. O roteador mantém até 4.096 entradas de deduplicação em memória. Uma nova tentativa recebe outro UUID, permitindo atravessar a malha novamente se a mensagem ou o ACK anterior se perdeu.

O destino não retransmite a mensagem recebida. Intermediários encaminham apenas para enlaces atualmente conectados, exceto o de entrada. O conteúdo protegido não é alterado. Nenhum intermediário recebe chave privada ou grava o pacote em SQLite.

O destinatário grava a mensagem e o recibo numa única transação. Uma repetição com o mesmo envelope retorna o mesmo recibo persistido, sem duplicar a conversa. Um UUID repetido com conteúdo conflitante é rejeitado.

## Persistência e limites operacionais

O banco `rastro-chat.db` é criado sem migração destrutiva dos dados existentes do projeto. São usadas tabelas próprias `contacts` e `messages`; versões futuras deverão adicionar migrações explícitas.

Dados sensíveis são cifrados por coluna, com dados associados vinculados à finalidade e ao ID da linha. Metadados de roteamento, IDs, estado e horários não são todos secretos. A criptografia ponta a ponta não fornece anonimato.

Banco e keysets são excluídos das regras de backup e transferência de dispositivo. Perda de chave não é corrigida gerando outra identidade silenciosamente. Limpar dados ou reinstalar exige novo cadastro e pode tornar o histórico anterior irrecuperável.

Limites iniciais:

- 2.048 bytes UTF-8 de texto por mensagem.
- 16 KiB por moldura de chat; limites internos adicionais no parser.
- 256 contatos e 10.000 mensagens no banco.
- Até 128 mensagens próprias pendentes.
- Até quatro mensagens da fila por ciclo de retentativa de 30 segundos; novas mensagens e mudanças de conectividade podem antecipar um ciclo.
- Histórico visual limitado às 200 mensagens mais recentes por conversa; as anteriores permanecem no banco.
- Fila de trabalho com 128 operações e admissão de até 24 pacotes de chat por segundo, para limitar carga independente do SOS.

Ao atingir a cota, a operação falha sem apagar o histórico. Não há limpeza automática de mensagens próprias ou expiração automática nesta Parte 1. Gestão avançada de retenção e expiração de custódias pertence à evolução seguinte.

A interface informa **última mensagem autenticada**, não localização nem encontro físico: uma mensagem pode chegar por intermediários. A ordenação das conversas usa o registro local, e emissão/recebimento são exibidos separadamente. Timeouts locais usam tempo monotônico.

Criptografia e SQL executam em uma fila de trabalho dedicada, fora da thread de interface. O serviço existente aciona a rede e as retentativas; instalar o app ou abrir apenas o histórico não inicia descoberta continuamente.

## Arquivos principais

- `chat/ChatProtocol.kt`: contatos públicos, envelope, moldura e roteador sem custódia.
- `chat/ChatCrypto.kt`: operações Tink e validação de chaves públicas.
- `chat/ChatVault.kt`: Keystore e persistência cifrada da identidade.
- `chat/ChatStore.kt`: SQLite, proteção de campos, transações e deduplicação.
- `chat/ChatRuntime.kt`: fila de execução, envio, recepção e retentativas.
- `ChatActivity.kt` e `res/layout/activity_chat.xml`: QR, contatos e conversas.
- `NearbyTransport.kt` e `RastroService.kt`: integração com o enlace e serviço existentes.

Os caminhos Kotlin são relativos a `app/src/main/java/com/example/rastro`.

## Validação

Resultado local em 30/09/2026: **98 testes aprovados, zero falhas**, APK do aplicativo e APK de testes instrumentados compilados. Lint concluído com **zero erros e 102 avisos** no projeto completo. Os 14 testes locais novos cobrem protocolo/criptografia e persistência/integração; os demais 84 já pertenciam à base.

Comandos:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Os testes locais incluem criptografia para destinatário correto, rejeição de falsificação/adulteração, parser, Unicode, limites, roteamento A → B → C, ACK autenticado, duplicatas, reabertura do SQLite, rollback, cota da fila e apelidos sem mudança de identidade. O SQLite é exercitado com Robolectric/API 28; isso não equivale a teste em aparelho.

Validação posterior da Parte 1 em dispositivo: **13 testes instrumentados aprovados** no Samsung SM-S926B, Android 16/API 36. Foram cobertos persistência/Keystore, QR gerado e decodificado, navegação/recriação de telas e regressões de mapa e observadores. A identidade também foi conferida após encerramento e reabertura do processo. A instalação foi preservada ao final.

Na Parte 2, esses testes foram executados novamente junto aos três novos testes de custódia, totalizando **16 aprovados**. Veja [resultados e comandos atuais](MELHORIAS_02_PARTE2.md).

Permanecem para ensaios físicos com múltiplos aparelhos: leitura de QR pela câmera, rádio em encontros separados, tela bloqueada e bateria. Nenhum ensaio de alcance foi declarado realizado.

## Evolução posterior

A [Parte 2](MELHORIAS_02_PARTE2.md) adicionou custódia durável de pacotes de terceiros e entrega diretamente ao destinatário. Não reutilizar o flooding conectado para implementar, inadvertidamente, repasse entre portadores. Consultar o plano para limites de cópias e complementos futuros.