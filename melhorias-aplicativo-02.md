# Propostas de melhorias do Rastro — 02

Data: 30/09/2026.

Status: Parte 1 implementada em software em 30/09/2026; veja [uso, arquitetura e limites da validação](docs/MELHORIAS_02_PARTE1.md). Parte 2 implementada em software; veja [portadores, uso e validação](docs/MELHORIAS_02_PARTE2.md). Evoluções futuras permanecem planejadas. Este documento substitui `melhorias-aplicativo-02.txt` e preserva os requisitos. Não representa auditoria de segurança ou validação física concluída.

## 1. Objetivo e decisões adotadas

Adicionar chat privado de texto e uma caixa de correspondência que permita armazenar mensagens e entregá-las posteriormente, mesmo quando remetente e destinatário não estiverem conectados simultaneamente.

Decisões do usuário:

- Cadastrar e verificar contatos por **QR Code**, inicialmente em encontro presencial.
- Implementar em duas partes: primeiro a base do chat privado; depois os portadores com armazenamento persistente.
- Na primeira versão dos portadores, permitir **entrega diretamente ao destinatário**. Um portador não repassa o pacote para outro portador.
- Deixar repasse entre portadores, número limitado de cópias e outros complementos para evoluções futuras.
- Manter o chat inicialmente restrito a texto.

A mensagem deve sair do remetente já criptografada para o destinatário. Um intermediário pode transportar o pacote, mas não deve possuir as chaves necessárias para ler seu conteúdo.

## 2. Contexto e limites da base atual

O projeto principal fica na raiz, com código Android em `app/src`. As melhorias 01 já possuem implementação documentada em `docs/MELHORIAS_01_IMPLEMENTACAO.md`, incluindo SOS com localização, eventos e mapa.

A base analisada contém:

- Kotlin, interface XML/AppCompat e serviço `RastroService`.
- Nearby Connections para descoberta e conexões entre aparelhos.
- `MeshRouter` com flooding, deduplicação e ACK para o protocolo existente.
- `Mensagem` v3, com leitura de versões anteriores e limite de 1.024 bytes.
- Identidade anunciada com UUID e nome, sem vínculo criptográfico verificado.
- Estado de eventos e deduplicação em memória; ausência de fila durável de chat.

Esses componentes podem ser aproveitados, mas não oferecem por si só chat privado, contatos persistentes ou entrega adiada. O ACK atual de SOS não deve ser reinterpretado como confirmação de entrega de chat.

## 3. Divisão da implementação

| Parte | Objetivo | Resultado esperado |
| --- | --- | --- |
| **Parte 1 — Chat privado e contatos** | Identidade criptográfica, cadastro por QR Code, armazenamento local e chat de texto | Dois contatos verificados conversam diretamente; posteriormente, dentro desta parte, o chat atravessa intermediários simultaneamente conectados sem expor o texto |
| **Parte 2 — Portadores com entrega direta** | Guardar pacotes de terceiros e entregá-los em encontros posteriores | A entrega a B; A fica indisponível; B encontra C mais tarde e entrega diretamente a C |
| **Futuro — Repasse e complementos** | Ampliar alcance temporal e recursos | Portador entrega a outro portador com controle de cópias, além dos complementos descritos ao final |

A Parte 1 deve ser concluída e validada antes da Parte 2. Preparar interfaces extensíveis não significa implementar antecipadamente os recursos futuros.

### Diferença entre os dois tipos de intermediário

- **Retransmissor da Parte 1:** encaminha durante uma conexão ativa da malha; não assume uma obrigação de carregar o pacote para um encontro futuro.
- **Portador da Parte 2:** aceita guardar o pacote em disco, sobrevive ao encerramento do processo e tenta entregá-lo em um encontro posterior diretamente com o destinatário.

Para a primeira versão da Parte 2, a seleção e transferência de custódia serão entre o remetente original e um portador diretamente conectado. O pacote em custódia somente poderá sair desse portador para o destinatário final diretamente conectado. Não enviá-lo pelo flooding geral, o que poderia introduzir repasse entre portadores sem intenção.

## 4. Parte 1 — Identidade criptográfica e contatos por QR Code

### 4.1. Identidade

Criar uma identidade criptográfica persistente para cada instalação, com material para criptografia e autenticação conforme o protocolo escolhido. Nome visual e UUID anunciado não são prova de identidade.

- Usar biblioteca e construções criptográficas consolidadas; não criar algoritmo próprio.
- Avaliar uma solução como Tink/HPKE para cifrar para destinatário offline, verificando compatibilidade com minSdk e dependências reais do projeto.
- Incluir autenticação do remetente: criptografia híbrida isolada não comprova autoria.
- Proteger chaves locais com Android Keystore onde compatível; material de biblioteca que precise ser persistido deve ser protegido por uma chave local apropriada.
- Nunca transmitir chaves privadas nem colocá-las em QR Code, logs ou anúncios Nearby.
- Planejar versão da identidade, identificador da chave e comportamento de troca de chave.

O protocolo criptográfico precisa ser definido e revisado antes da implementação. A escolha de HPKE por si só não oferece todas as propriedades de um protocolo de mensagens como o Signal, incluindo proteção de mensagens antigas após comprometimento futuro de chaves.

### 4.2. Cadastro presencial por QR Code

Fluxo recomendado:

1. O usuário abre **Meu QR Code** no aparelho do contato que deseja cadastrar.
2. O outro aparelho lê o código com a câmera, valida seu formato e mostra os dados para confirmação.
3. O usuário confirma o nome/identidade da pessoa com quem está presencialmente.
4. O aplicativo salva a identidade criptográfica e marca o contato como verificado por QR Code.
5. Repetir no sentido inverso para cadastro mútuo antes de iniciar a conversa.

O cadastro mútuo evita que o destinatário receba a primeira mensagem sem uma referência previamente verificada da identidade do remetente. Uma troca automática equivalente pode ser avaliada depois, desde que tenha prova criptográfica adequada.

O QR Code deve conter apenas dados públicos necessários, como versão, identificador estável, chaves públicas ou representação equivalente, identificação das chaves e nome sugerido. Validar tamanho, versão, estrutura e algoritmos aceitos.

Ler um QR Code não comprova sozinho a identidade humana de uma pessoa desconhecida. A confiança inicial vem da apresentação presencial do código no aparelho esperado e da confirmação pelo usuário.

### 4.3. Registro dos contatos

Persistir:

- Identificador estável e vínculo com a identidade criptográfica.
- Chaves públicas e identificação/versão das chaves.
- Nome anunciado e apelido local, que podem ser diferentes.
- Data do cadastro e estado de verificação.
- Último encontro observado, sem confundi-lo com localização atual.

Um contato salvo continua disponível fora de alcance. Nomes iguais não devem fundir contatos distintos.

Se uma identidade conhecida aparecer com outra chave, bloquear a substituição silenciosa e exigir nova verificação. Na primeira versão, reinstalação ou perda das chaves exige novo cadastro; não prometer recuperação automática de mensagens destinadas às chaves antigas.

A decisão anterior de adiar autenticação geral da malha não elimina a necessidade de verificar as identidades dos participantes do chat. Portadores podem continuar sem cadastro como contato pessoal, mas não são considerados confiáveis quanto à entrega.

## 5. Parte 1 — Chat privado de texto

### 5.1. Fluxo

1. Selecionar um contato verificado.
2. Escrever texto dentro do limite definido pelo aplicativo.
3. Persistir o evento de envio e gerar um envelope criptográfico para o destinatário.
4. Enviar diretamente quando o contato estiver conectado.
5. Na etapa seguinte desta parte, permitir encaminhamento por uma malha simultaneamente conectada.
6. O destinatário verifica integridade, autoria e destino, decifra e registra a mensagem de forma transacional.
7. Somente após registro durável, emitir confirmação autenticada vinculada à mensagem.

O remetente pode guardar uma cópia protegida em seu histórico. Intermediários não recebem texto aberto. Se não houver caminho, a mensagem permanece na caixa de saída local para tentativa posterior; na Parte 1 ainda não é entregue a portadores para custódia.

### 5.2. Envelope e protocolo

Separar conteúdo privado de dados necessários ao transporte. Campos conceituais:

| Informação | Uso |
| --- | --- |
| Versão e tipo | Distinguir chat, confirmação, inventário e controle |
| ID único da mensagem | Deduplicação, referências e confirmação |
| Identidades/chaves de origem e destino | Autenticação e seleção do destinatário |
| Conteúdo cifrado | Texto e dados privados associados |
| Proteção criptográfica de integridade/autoria | Detectar adulteração e falsificação |
| Criação e política de validade | Exibição e expiração operacional |
| Limites de transporte | Tamanho e política de encaminhamento |

Proteger criptograficamente os campos imutáveis relevantes para impedir troca de destino, ID ou conteúdo. Separar contadores mutáveis de encaminhamento desses dados. Não remover a verificação de integridade para permitir alteração de campos de rota.

Metadados necessários ao transporte, como destino, tamanho e validade, podem ficar visíveis ao portador. Confidencialidade do texto não implica anonimato.

Versionar os novos tipos e revisar o limite atual de 1.024 bytes, considerando texto UTF-8, cabeçalhos, assinaturas e sobrecarga criptográfica. Definir limites em bytes antes da interface. Atualizar todos os aparelhos dos ensaios; leitura de formatos antigos não torna instalações antigas compatíveis com chat novo.

Não enviar o chat como texto em campos existentes do SOS nem rebaixar para texto aberto quando houver incompatibilidade.

### 5.3. Confirmações e histórico

- Apenas confirmação autenticada do destinatário pode marcar a mensagem como entregue.
- ACK do enlace, sucesso do Nearby ou aceitação por intermediário não significam entrega final.
- Confirmar a mesma mensagem repetida deve ser seguro e não gerar duplicação na conversa.
- Persistir IDs de mensagens e recibos pelo período necessário à deduplicação.
- Manter horários de emissão e recebimento distintos; não presumir relógios sincronizados.
- Ordenar a interface de forma determinística mesmo quando houver entregas atrasadas.
- Não registrar texto aberto ou chaves em logs de diagnóstico.

**Entregue** significa recebida e salva pelo aplicativo destinatário, não lida por uma pessoa. Confirmação de leitura fica para uma evolução.

## 6. Persistência e organização local

Usar SQLite conforme a decisão D-03 existente, com proteção dos campos sensíveis. Não é necessário migrar o projeto para outro mecanismo apenas para esta entrega.

Separar conceitualmente:

| Conjunto | Conteúdo |
| --- | --- |
| Contatos | Identidades verificadas, chaves públicas e nomes |
| Conversas | Histórico do próprio usuário, protegido localmente |
| Caixa de saída | Mensagens próprias e estado de entrega |
| Pacotes transportados | Conteúdo cifrado de terceiros, somente na Parte 2 |
| Recibos e deduplicação | Confirmações autenticadas e IDs já processados |
| Tentativas | Informações limitadas para retentativa e diagnóstico |

Na Parte 1, implementar as tabelas e operações necessárias ao chat. A Parte 2 amplia o banco para custódia e sincronização. Preparar migrações, operações fora da thread principal e transações para não confirmar uma gravação que falhou.

Recriar a Activity, encerrar o processo ou reiniciar o aparelho não deve apagar dados persistidos. Perder as chaves, desinstalar ou limpar os dados do app são situações distintas e podem tornar mensagens irrecuperáveis.

## 7. Parte 2 — Portadores com entrega diretamente ao destinatário

### 7.1. Fluxo principal

```text
Encontro 1: A → B
A é remetente; B aceita guardar uma cópia cifrada destinada a C.

Intervalo: A e B se separam.
B conserva o pacote em disco.

Encontro 2: B → C
B entrega diretamente a C; C valida, decifra e salva.

Retorno: C → B → A, em encontros separados.
O recibo autenticado pode ser carregado por B até encontrar A.
```

Nenhuma conexão simultânea entre A e C é necessária. B não pode entregar o pacote a D para que D procure C nesta primeira versão.

### 7.2. Política inicial de cópias

Adotar inicialmente **um portador ativo por mensagem**, além da cópia mantida pelo remetente. Não transferir exclusivamente a única cópia para o portador.

- Registrar a aceitação de custódia somente após gravação durável em B.
- Tornar pedidos repetidos ao mesmo portador idempotentes.
- Se a resposta de custódia se perder, consultar/repetir com o mesmo portador; não distribuir automaticamente para novos portadores.
- A atribuição pode permanecer incerta após uma interrupção. Não prometer contagem exata de cópias físicas diante de falhas ou aparelhos maliciosos.
- Substituição de portador e distribuição para múltiplos portadores ficam para uma política futura explícita; uma cópia antiga não pode ser revogada remotamente sem reencontro.

A cópia local continua permitindo entrega direta pelo remetente. Se A e B entregarem a mesma mensagem, C deve exibi-la uma única vez.

### 7.3. Encontros e sincronização

Ao estabelecer conexão com um aparelho compatível:

1. Trocar capacidades e versão do protocolo.
2. Comparar inventários limitados de IDs, sem transmitir textos abertos.
3. Priorizar recibos pendentes e entrega ao destinatário diretamente encontrado.
4. Permitir novas custódias apenas quando o par é o remetente original, o modo portador está ativo e há capacidade.
5. Transferir apenas dados faltantes e confirmar gravação.
6. Registrar resultados e retentar de maneira controlada após interrupções.

O nome anunciado não prova que o par é o destinatário. Usar prova de posse da chave correspondente quando a identidade for necessária ao controle do fluxo. A confirmação final deve ser autenticada pelo destinatário, independentemente da identidade anunciada no Nearby.

O portador não precisa ser contato pessoal de A ou C para carregar conteúdo cifrado. Ele ainda pode recusar, descartar ou atrasar pacotes; criptografia não garante disponibilidade.

### 7.4. Recibos de custódia e entrega

Distinguir:

- **Recepção de transporte:** o enlace recebeu bytes.
- **Custódia aceita:** o portador informa que gravou o pacote.
- **Entrega confirmada:** o destinatário verificou e salvou a mensagem, emitindo recibo autenticado.

O recibo final deve referenciar ID e identidade corretos, com proteção contra falsificação e reaproveitamento para outra mensagem. Guardar recibos e marcadores de entrega por prazo definido para responder a retransmissões sem reabrir a conversa como nova mensagem.

B pode carregar o recibo emitido por C e entregá-lo diretamente a A. Isso é o retorno de uma confirmação ao seu destinatário, não repasse do pacote original entre portadores. C também pode confirmar diretamente a A se ambos se encontrarem.

A pode continuar vendo **Aguardando confirmação** mesmo quando C já recebeu, pois o recibo também depende dos encontros. Não afirmar que uma mensagem não foi entregue apenas porque o recibo não voltou.

### 7.5. Capacidade e expiração

Definir limites de tamanho por mensagem, pacotes e bytes totais, inclusive por origem quando aplicável. Identidades não verificadas podem ser recriadas, portanto limites por origem não substituem uma cota global.

- Recusar novas custódias se não houver espaço, sem apagar silenciosamente mensagens próprias.
- Aplicar validade operacional para evitar armazenamento infinito.
- Tratar relógios divergentes, reinício e mudança de horário na política de expiração.
- Usar tempo monotônico para esperas locais durante a execução; ele não fornece uma referência universal entre aparelhos ou reinícios.
- Definir retenção de recibos/IDs de modo coerente com a vida útil dos pacotes.
- Reservar capacidade e prioridade para SOS e controles da malha.
- Limitar inventários, taxa de envio e retentativas para evitar excesso de bateria e tráfego.

Valores concretos de validade, cotas e frequência devem ser escolhidos e documentados durante a implementação e os testes. Não apresentar expiração como prova de que o destinatário nunca recebeu a mensagem.

## 8. Interface e operação

Adicionar telas ou áreas de **Contatos**, **Conversas**, **Meu QR Code** e **Ler QR Code**. Para câmera negada ou indisponível, explicar a impossibilidade de cadastro por esse método sem travar o restante do app.

Estados sugeridos:

| Estado | Significado |
| --- | --- |
| Aguardando conexão | Salva localmente, sem envio confirmado |
| Custódia em confirmação | Transferência ao portador iniciada, gravação ainda não confirmada |
| Copiada para portador | Portador informou armazenamento durável |
| Aguardando confirmação do destinatário | Não chegou recibo final válido |
| Entrega confirmada | Recibo autenticado recebido |
| Expirada sem confirmação | Prazo operacional encerrado sem recibo final |
| Falha de identidade ou validação | Chave alterada, pacote inválido ou outra falha que exige tratamento |

A Parte 2 deve oferecer controle explícito para **Participar como portador**, com indicação de uso de armazenamento e pacotes pendentes. Desativar novas custódias não deve descartar automaticamente as já aceitas; definir ação separada para remoção local e explicar sua consequência.

A descoberta ocorre enquanto o modo de operação estiver ativo, com serviço em primeiro plano e notificação apropriada. Apenas ter o app instalado não garante procura contínua: Android, permissões, rádios e bateria limitam a execução. Retomar dados persistidos não equivale a garantir reinício automático da procura após qualquer encerramento.

## 9. Ordem recomendada detalhada

### Parte 1 — Concluir antes dos portadores

1. Definir identidade, protocolo criptográfico e formato versionado dos envelopes.
2. Implementar proteção das chaves e contatos por QR Code com cadastro mútuo.
3. Implementar persistência de contatos, conversas, caixa de saída e deduplicação.
4. Implementar chat de texto direto, integridade/autoria e recibo final autenticado.
5. Implementar interface de contatos e conversas com estados claros.
6. Acrescentar encaminhamento cifrado pela malha simultaneamente conectada, preservando os dados protegidos.
7. Validar segurança funcional, reinício, duplicatas e regressões de SOS/mapa.

**Critério de conclusão:** A e C, cadastrados por QR Code, trocam texto diretamente e via B conectado à malha. B não possui acesso ao texto; C registra uma única mensagem válida; A só mostra entrega após recibo final válido. Histórico e identidade sobrevivem ao encerramento do processo.

### Parte 2 — Entrega adiada com um portador

1. Criar armazenamento de pacotes de terceiros e controle do modo portador.
2. Implementar inventário, oferta e aceitação durável de custódia.
3. Aplicar a política de um portador ativo por mensagem, mantendo cópia na origem.
4. Implementar entrega exclusivamente ao destinatário diretamente encontrado.
5. Implementar retorno persistente do recibo por esse portador ou contato direto.
6. Acrescentar expiração, cotas, retentativas e recuperação de interrupções.
7. Validar em encontros separados, com remetente indisponível e reinício do portador.

**Critério de conclusão:** A entrega a B e fica indisponível. Após reiniciar, B encontra C e entrega uma única mensagem válida sem revelar o texto. B posteriormente encontra A e devolve confirmação autenticada. Encontrar D antes de C não transfere a custódia de A para D.

## 10. Organização sugerida do código

Os nomes abaixo são propostas, não classes já implementadas:

| Área | Responsabilidade |
| --- | --- |
| IdentidadeCriptografica / CriptografiaChat | Gerenciar identidade e operações criptográficas |
| RepositorioContatos | Cadastro, verificação e troca de chave |
| RepositorioConversas | Histórico, saída e estado de entrega |
| EnvelopeChat | Formato, limites e validação do protocolo |
| RoteadorChat | Encaminhamento de chat com conexões simultâneas |
| RepositorioCustodia | Pacotes de terceiros e recibos persistentes |
| SincronizadorPortador | Inventários e entrega direta durante encontros |

Reaproveitar `NearbyTransport` como enlace e `RastroService` para coordenar operação. Evitar concentrar criptografia, SQL e interface em uma classe única. Preservar o tratamento de SOS existente e separar sua política de ACK e flooding das regras de chat/custódia.

## 11. Testes e validação

### Parte 1

- Cadastro QR válido, duplicado, malformado, incompatível e com identidade/chave divergente.
- Troca de chave exigindo nova verificação.
- Mensagem abrindo apenas com chave apropriada e autoria válida.
- Rejeição de alteração no conteúdo, destino, ID ou autenticação.
- Confirmação falsa não marcando entrega.
- Unicode e limites em bytes, incluindo sobrecarga criptográfica.
- A → B → C conectado, com inspeção de logs/armazenamento de B sem texto aberto.
- Duplicatas e repetição de recibos sem mensagens duplicadas na interface.
- Persistência após recriação da Activity e encerramento do processo.
- Regressão de SOS, mapa e versões anteriores do protocolo suportadas.

### Parte 2

- A → B, desligamento/indisponibilidade de A, reinício de B e encontro posterior B → C.
- B encontra D e não lhe repassa o pacote destinado a C.
- Retorno do recibo C → B e depois B → A.
- Falha antes/depois de gravar custódia, inclusive perda da resposta a A.
- Entrega direta por A e posterior repetição por B sem duplicação em C.
- Espaço insuficiente, cotas, expiração, horários divergentes e transferência interrompida.
- Destinatário anunciado com chave falsa e portador que descarta pacotes.
- Tela bloqueada, permissões, perda de conexão e consumo de bateria em aparelhos físicos.

Executar na raiz as verificações apropriadas:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Testes instrumentados exigem aparelho/emulador preparado. Testes automatizados de lógica não substituem ensaios físicos de rádio nem revisão do desenho criptográfico.

## 12. Implementações futuras

| Evolução | Condições e cuidados |
| --- | --- |
| Repasse entre portadores | Permitir B → D → C com regras explícitas; não habilitar por acidente no flooding |
| Múltiplas cópias limitadas | Definir orçamento e distribuição, por exemplo abordagem spray-and-wait, com testes de interrupções e duplicação |
| Melhor seleção de portadores | Considerar encontros anteriores e custo, sem tratar previsão como garantia |
| Recibos por múltiplos caminhos | Propagar com limites e deduplicação para reduzir a demora das confirmações |
| Substituição de portador | Tratar cópias antigas ainda existentes e impossibilidade de revogação instantânea |
| Confirmação de leitura | Separar leitura humana de recebimento técnico, com controle do usuário |
| Cancelamento | Explicar que apagar localmente não elimina imediatamente cópias em portadores desconectados |
| Recuperação e rotação de chaves | Definir migração, nova verificação e tratamento de mensagens para chaves antigas |
| Segurança criptográfica avançada | Avaliar protocolo com sigilo futuro e recuperação após comprometimento, além de revisão especializada |
| Anexos e grupos | Rever tamanho, fragmentação, criptografia para múltiplos destinatários e armazenamento |
| Negociação de versões | Definir interoperabilidade sem rebaixar segurança silenciosamente |
| Confiança dos portadores | Avaliar pareamento de transporte, bloqueios e mitigação de abuso sem confundir isso com confidencialidade ponta a ponta |
| Privacidade de metadados | Reduzir exposição de relações e inventários, além da proteção do texto |

Mesmo com orçamento de cópias, um aparelho malicioso pode copiar os bytes que recebe. Os limites de distribuição organizam o comportamento dos participantes cooperativos; não são uma garantia criptográfica de número absoluto de cópias.

## 13. Limitações que a interface e a documentação devem preservar

- A entrega depende de uma sequência de encontros e do funcionamento dos aparelhos; não há garantia absoluta de prazo ou chegada.
- Portadores não precisam ler o conteúdo para descartá-lo ou atrasá-lo.
- Sem retorno de recibo, o remetente pode desconhecer uma entrega já realizada.
- Criptografia protege o conteúdo durante transporte e armazenamento conforme o desenho adotado; não resolve comprometimento do aparelho destinatário.
- Nome, UUID e descoberta Nearby não substituem verificação criptográfica do contato.
- Não anunciar equivalência de segurança com WhatsApp ou Signal apenas por usar criptografia.

## 14. Referências

- [Melhorias 01](melhorias-aplicativo-01.md).
- [Implementação das melhorias 01](docs/MELHORIAS_01_IMPLEMENTACAO.md).
- [Decisões de arquitetura](docs/DECISIONS.md).
- [Nearby: gerenciamento e autenticação de conexões](https://developers.google.com/nearby/connections/android/manage-connections).
- [Tink: criptografia híbrida e limites de autenticação](https://developers.google.com/tink/hybrid).
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore).
- [Serviços em primeiro plano no Android](https://developer.android.com/develop/background-work/services/fgs).
- [RFC 9171: modelo store-carry-forward](https://www.rfc-editor.org/rfc/rfc9171.html).
