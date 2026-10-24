# Propostas de melhorias do Rastro — 03

## 1. Objetivo e decisão adotada

Evoluir o transporte de mensagens para permitir **repasse entre portadores, com orçamento pequeno e recuperação confiável de interrupções**, preservando o chat privado, a entrega direta, a malha conectada, o mapa e o SOS existentes.

O foco do Rastro é permitir comunicação entre dispositivos Android sem depender da internet para transportar mensagens. Origem e destino podem estar distantes e indisponíveis ao mesmo tempo: aparelhos intermediários aproximam esses pontos por conexões e encontros sucessivos.

A distância não elimina os limites físicos. A entrega exige uma sequência viável de conexões ou encontros antes de encerrar os limites operacionais. Não há garantia de alcance ilimitado, prazo ou chegada.

**Decisões para esta evolução:**

- Implementar primeiro repasse com poucas autorizações de transporte e tratamento persistente de falhas.
- Manter criptografia ponta a ponta e cadastro mútuo por QR Code entre remetente e destinatário.
- Não exigir contatos cadastrados nos portadores.
- Manter a cópia própria do remetente.
- Não aumentar o orçamento automaticamente após timeout, reinício, descarte ou ausência de confirmação.
- Tratar seleção inteligente de portadores e novos transportes como **features futuras**.
- Manter autenticação de conta, anexos e grupos fora desta implementação inicial.

**Status:** implementação inicial disponível no código, com testes locais e ensaios controlados nos três emuladores. A validação de rádio em aparelhos físicos permanece pendente. Consulte [implementação, limites efetivos e resultados](docs/MELHORIAS_03_IMPLEMENTACAO.md). As features futuras da seção 16 continuam fora desta entrega; as seções abaixo preservam o plano original.

O usuário informou que validou os gestos de navegação no aparelho e que estão funcionando corretamente. Essa validação pertence à navegação já implementada e não comprova o funcionamento do novo protocolo de repasse.

## 2. O que já existe e deve ser preservado

Referências:

- [Plano das melhorias 02](melhorias-aplicativo-02.md).
- [Chat privado — Parte 1](docs/MELHORIAS_02_PARTE1.md).
- [Portadores com entrega direta — Parte 2](docs/MELHORIAS_02_PARTE2.md).

A implementação atual utiliza Kotlin, layouts XML/AppCompat, SQLite, Tink, Nearby Connections e RastroService. Já oferece:

- Identidades criptográficas persistentes e contatos por QR Code.
- Mensagens cifradas e assinadas.
- Histórico, fila própria, recibos e deduplicação persistentes.
- Entrega direta e encaminhamento pela malha simultaneamente conectada.
- Um portador atribuído por mensagem, que entrega diretamente ao destinatário.
- Retorno do recibo pelo portador.
- Modo portador opcional, cotas, expiração e remoção local confirmada.
- Telas alinhadas ao design do aplicativo.
- Navegação contínua entre Início, Dispositivos e Histórico.

Esta proposta substitui, para mensagens do novo protocolo, a restrição de entrega exclusivamente direta pelo portador. Não exige reescrever o chat, trocar SQLite, alterar o protocolo de SOS ou migrar a interface para outra tecnologia.

## 3. Contatos pessoais e transporte são funções distintas

**O portador já não precisa ter o remetente nem o destinatário cadastrados como contato.**

O teste atual de custódia verifica B transportando a mensagem e devolvendo a confirmação com sua lista de contatos vazia.

| Participante | Necessidade inicial |
| --- | --- |
| A, remetente | Cadastrar C e obter suas chaves públicas verificadas |
| C, destinatário | Cadastrar A para aceitar sua mensagem privada |
| B, D e E, portadores | Participar do transporte; não cadastrar A ou C |
| Aparelhos em um encontro | Provar posse da identidade criptográfica utilizada no protocolo |

A apresentação de chaves públicas durante um encontro não deve inserir automaticamente ninguém na lista de contatos nem conferir confiança pessoal.

O bloqueio atual é a exigência de que o aparelho oferecendo custódia seja a origem do envelope. CustodyEngine e CustodyStore aplicam essa regra. A evolução precisa separar **autor da mensagem**, **portador anterior** e **portador atual**, mantendo a verificação de cada papel.

Não remover a exigência de contato do destinatário para resolver uma suposta exigência do portador: são verificações diferentes.

## 4. Fluxo esperado

~~~text
A cria uma mensagem cifrada para C.

Encontro 1: A → B
B aceita o pacote e uma autorização de transporte.

Encontro 2: B → D
D aceita a mesma mensagem e a autorização delegada por B.

Encontro 3: D → E
E assume o transporte dessa autorização.

Encontro 4: E → C
C verifica a autoria, decifra e salva uma única mensagem.

Retorno:
A confirmação autenticada de C é transportada até A.
Pode aproveitar outros encontros, sem exigir a cadeia inversa completa.
~~~

A pode ficar indisponível após o primeiro encontro. B, D e E não recebem chaves privadas nem precisam acessar o texto.

A mensagem pode chegar também por um caminho direto ou pela malha conectada. Seu ID e hash permanecem os mesmos; C não cria outra entrada na conversa.

## 5. Política inicial de orçamento pequeno

### 5.1. Duas linhas de transporte, além da cópia própria

Como ponto de partida concreto, propor **duas autorizações de transporte por mensagem**, emitidas pela origem, além da cópia própria mantida em A.

Cada autorização possui um identificador estável e funciona como uma linha independente de transporte:

~~~text
Cópia própria: A conserva a mensagem.

Linha 1: A → B → D → E → C
Linha 2: A → F → G → C
~~~

Cada linha permite um responsável ativo por vez entre participantes cooperativos. Repassar a autorização move a responsabilidade; não cria uma terceira linha.

A origem registra as duas autorizações uma única vez. Não pode recriá-las após reinício, trocar seus IDs para renovar a distribuição ou reemitir uma autorização cuja situação é incerta.

Se um aparelho receber ambas as linhas, pode armazenar o conteúdo cifrado uma vez e manter dois registros independentes de autorização. Isso não aumenta o orçamento.

Esta política é uma proposta conservadora específica para o Rastro. Não deve ser apresentada como implementação integral de um algoritmo padronizado de roteamento.

### 5.2. O que o orçamento realmente limita

Distinguir:

- **Autorização ativa:** direito de continuar oferecendo aquela linha a outro portador.
- **Cópia física:** bytes que ainda podem existir em um aparelho, inclusive durante uma transferência incerta.
- **Cópia própria da origem:** histórico e caixa de saída de A.
- **Histórico final:** mensagem recebida por C.

Duas autorizações não significam exatamente dois arquivos físicos. Durante interrupções, o portador anterior pode conservar bytes sem ter autorização para repassá-los novamente.

Um aparelho malicioso pode copiar bytes ou violar o protocolo. O orçamento limita o comportamento do software cooperativo; não fornece uma contagem global inviolável sem coordenação externa.

### 5.3. Política de repasse

- Priorizar entrega ao destinatário autenticado diretamente encontrado.
- Permitir repasse a outro aparelho compatível que aceite custódia e tenha capacidade.
- Não devolver uma linha a identidades já presentes na cadeia de delegação.
- Não permitir que a mesma autorização seja oferecida simultaneamente a dois destinos.
- Não usar timeout como prova de que uma transferência falhou antes de ser gravada.
- Permitir entrega final a C a partir de uma cópia válida ainda disponível, sem consumir outra autorização. Isso não autoriza novo repasse a intermediários.
- Ao esgotar o limite de repasses, conservar a possibilidade de entrega direta a C até expirar; não reiniciar a cadeia.

Sem histórico de encontros, o primeiro portador elegível não é necessariamente melhor. A versão inicial aplica uma escolha simples, determinística e com intervalo mínimo entre novas delegações da mesma linha. O intervalo deve ser configurável e calibrado nos testes para evitar repasses sucessivos inúteis em um grupo parado.

## 6. Transferências persistentes e recuperação de interrupções

Esta é a prioridade da primeira implementação.

Cada transferência deve possuir:

- ID único e persistente.
- ID/hash da mensagem e ID da autorização.
- Identidade criptográfica de quem delega e de quem recebe.
- Referência à delegação anterior e contador de repasses.
- Prazo restante e política aplicável.
- Estado local, tentativas e confirmação verificável.

### 6.1. Sequência segura proposta

1. B consulta a capacidade e o inventário de D.
2. Antes de enviar uma delegação válida, B registra em transação a transferência para D e **bloqueia localmente o uso dessa autorização**.
3. B envia o pacote, a política e a prova de delegação para D.
4. D verifica tudo, aplica suas cotas e grava pacote, autorização e transferência numa transação.
5. Somente depois dessa gravação D responde com uma confirmação autenticada de armazenamento.
6. B registra a confirmação e aposenta seu direito de repasse daquela linha. Pode liberar o conteúdo se nenhuma outra finalidade local exigir sua retenção.

D pode assumir a autorização após a gravação válida porque B já a bloqueou antes de emitir a delegação. A ativação não depende de uma última confirmação volátil enviada por B.

### 6.2. Estados mínimos sugeridos

| Estado local | Significado |
| --- | --- |
| ATIVA | Autorização disponível para entrega ou delegação |
| SAIDA_PENDENTE | Delegação persistida para um par; autorização bloqueada no cedente |
| ACEITA | Receptor gravou e assumiu a autorização |
| REPASSADA | Cedente recebeu a confirmação e aposentou sua autorização |
| CONCLUIDA | Há confirmação final verificável |
| EXPIRADA | Prazo encerrado |
| REMOVIDA | Usuário removeu a cópia local |

Os nomes são propostas. O importante são as transições e invariantes, não a nomenclatura.

### 6.3. Respostas perdidas, reinícios e recusa

- Repetir o mesmo ID retorna o mesmo resultado; não cria outra autorização.
- Se B reiniciar, SAIDA_PENDENTE continua bloqueada para terceiros.
- Se D já gravou mas a resposta se perdeu, o reencontro deve recuperar a confirmação.
- Se a oferta nem chegou a D, B ainda não pode concluir isso apenas pelo silêncio.
- Na primeira versão, uma transferência incerta pode ficar bloqueada até reencontro ou expiração.
- Uma recusa anterior à emissão da delegação permite escolher outro par.
- Uma recusa posterior à emissão não libera automaticamente a autorização. Cancelamento seguro exigiria um protocolo próprio e marcadores duráveis no receptor; fica fora da versão inicial.
- Remover bytes localmente não libera orçamento para uma nova delegação.
- O protocolo deve tolerar crash antes e depois de cada gravação e envio, sem depender de callbacks em memória.

Essa escolha sacrifica alguma disponibilidade em cenários incertos para evitar distribuição duplicada. Não prometer transação atômica entre aparelhos desconectados.

## 7. Autoria, identidade e proteção criptográfica

Preservar o envelope cifrado existente sempre que possível. Acrescentar uma estrutura versionada de transporte ao redor dele.

### 7.1. Dados imutáveis verificáveis

A origem deve autenticar:

- ID e hash do envelope.
- Identidades de origem e destino.
- Versão e política de transporte.
- IDs e orçamento das autorizações.
- Validade máxima e limite de repasses.

O material público necessário para verificar a origem deve acompanhar o pacote, com formato e tamanho limitados. A impressão digital calculada das chaves deve coincidir com a identidade da origem.

A política assinada deve estar vinculada ao hash do envelope. Não aceitar combinação de política de uma mensagem com conteúdo de outra.

### 7.2. Delegações

Cada repasse deve vincular a autorização ao próximo portador e ser assinado pelo responsável anterior. D verifica a cadeia a partir da autorização emitida por A, mesmo sem A nos contatos.

Verificar continuidade, IDs, destinatário da delegação, contador e limites. Uma assinatura do portador anterior não substitui a assinatura original da mensagem.

Provas acumuladas precisam ter tamanho máximo. Não aumentar silenciosamente o limite de payload do transporte: medir a sobrecarga e definir um formato que caiba, ou rejeitar explicitamente o pacote. Fragmentação fica para uma evolução se for necessária.

A cadeia registra delegações declaradas, não comprova trajeto físico nem distância.

### 7.3. Sessão e confiança

Manter a prova de posse de chave e os desafios novos em cada conexão. IDs de transferência persistem entre sessões, mas comandos antigos de uma sessão não devem ser aceitos como comandos de uma nova.

Validar o formato das chaves e assinaturas antes de gravar. Nunca permitir fallback para texto aberto.

As provas não impedem retenção maliciosa, clonagem de estado, identidade descartável ou descarte do pacote. Cotas globais continuam obrigatórias.

## 8. Recibos que também atravessam portadores

Não vincular o sucesso do retorno à disponibilidade de B, D e E na ordem inversa.

Preservar o ACK cifrado já utilizado pelo chat e planejar uma prova de entrega verificável externamente, assinada por C e vinculada a:

- ID/hash da mensagem original.
- Identidades de A e C.
- Hash do ACK cifrado, quando encapsulado.

O objetivo dessa prova é permitir que intermediários validem o transporte e a conclusão sem decifrar a conversa. Ela não deve revelar o texto. Avaliar e documentar a exposição adicional de metadados antes de fixar seu formato.

Somente C emite a confirmação final. Confirmação de custódia de B ou D nunca marca a conversa como entregue.

Propor inicialmente duas linhas de retorno para cada recibo, com orçamento independente, deduplicação e os mesmos cuidados de transferência. O recibo deve ser persistido uma única vez junto da mensagem recebida; retransmissões não criam recibos nem orçamentos novos.

Priorizar recibos nas filas e reservar capacidade para eles, inclusive quando sua origem for um destinatário que não participa como portador de mensagens de terceiros.

Uma prova final válida permite interromper retentativas e limpar cópias locais associadas. Propagação de provas de conclusão também precisa de cotas; nunca transformar a limpeza em flooding ilimitado.

Um recibo pode chegar depois da expiração operacional da mensagem e ainda confirmar o histórico de A.

## 9. Coordenação dos caminhos disponíveis

Reutilizar o mesmo envelope e a mesma deduplicação para:

1. Entrega direta ao destinatário.
2. Encaminhamento pela malha atualmente conectada.
3. Transporte persistente por portadores em encontros posteriores.

Introduzir um coordenador de entrega que evite tentativas redundantes e priorize recibos e destinos presentes. Uma rota observada é oportunidade de tentativa, não garantia de entrega.

Se uma cópia transportada aproveitar a malha conectada, os retransmissores dessa tentativa não recebem automaticamente custódia durável nem novas autorizações. A criação de uma custódia continua exigindo o protocolo explícito.

O modo portador permanece opcional. Desativá-lo recusa novas responsabilidades, mantendo o tratamento das já aceitas. Nenhuma migração deve ativá-lo silenciosamente.

Manter o processamento de chat/custódia separado do SOS. O rádio compartilhado exige limites de tráfego e justiça entre filas.

## 10. Limites iniciais propostos

| Item | Proposta inicial |
| --- | --- |
| Autorizações de mensagem | 2, além da cópia própria |
| Autorizações de retorno por recibo | 2, independentes das de mensagem |
| Validade de mensagem | Manter orçamento máximo de 7 dias |
| Validade de retorno | Até 7 dias desde a criação persistente do recibo |
| Repasses de cada linha | Até 16; contador nunca reinicia |
| Pacotes ativos por portador | Manter limite global inicial de 128 |
| Pacotes ativos por origem | Manter limite inicial de 8 |
| Conteúdo e reservas | Manter orçamento inicial de 2 MiB |
| Retentativa | Reaproveitar ciclo de 30 segundos, com lotes limitados |
| Retenção de marcadores | Cobrir validade e reconciliação; para o novo fluxo, dimensionar para até 14 dias após a conclusão/expiração local |

São parâmetros iniciais para ensaio, não promessas de eficiência ou limites já implementados no novo protocolo.

O orçamento em bytes deve incluir envelopes, recibos, chaves públicas, provas e reservas. Registros de delegação e marcadores também precisam de cotas próprias. Se o limite atual de 2.048 marcadores for insuficiente, recusar novas admissões ou revisar explicitamente a capacidade; não apagar marcadores necessários à segurança de transferências pendentes.

Contabilizar cotas por origem criptográfica real e globalmente. Trocar de portador não reinicia a cota por origem.

Cada delegação reduz o orçamento temporal; não renova a validade. Manter tempo monotônico no mesmo boot e tratamento conservador de relógios após reinício. Os aparelhos não compartilham um relógio confiável: a política assinada limita o permitido, mas não comprova que um aparelho malicioso respeitou o tempo.

Expiração ou remoção local não prova que o destinatário nunca recebeu.

## 11. Banco e migração

Ampliar o SQLite existente com migração aditiva e transacional. A base atual está na versão 2; conferir a versão efetiva do projeto antes de escolher o próximo número.

Conjuntos sugeridos:

| Conjunto | Responsabilidade |
| --- | --- |
| Políticas de transporte | Versão, envelope associado, orçamento e limites assinados |
| Autorizações | ID estável, responsável, cadeia e estado |
| Transferências | ID da operação, pares, bloqueio, confirmação e retomada |
| Conteúdo transportado | Envelope compartilhado por autorizações compatíveis |
| Recibos em transporte | Confirmações persistentes e orçamento de retorno |
| Marcadores | Deduplicação, conclusão, remoção e operações já processadas |

Preservar contatos, chaves, mensagens próprias, históricos, preferências e custódias antigas.

**Não converter automaticamente custódias antigas em novas autorizações.** Um portador não pode fabricar uma política assinada pela origem. Mensagens antigas continuam no fluxo de entrega direta da Parte 2. Habilitar inicialmente o novo orçamento somente para mensagens criadas sob o novo protocolo.

Manter operações de SQL e criptografia fora da thread principal. Não confirmar gravação ou liberação antes do commit.

## 12. Organização do código

| Componente atual | Evolução proposta |
| --- | --- |
| ChatProtocol / ChatCrypto | Preservar o envelope; definir validação da política, provas e chaves públicas |
| CustodyProtocol | Nova versão, negociação de capacidade, inventário e transferência |
| CustodyStore | Migração, autorizações, operações persistentes, cotas e marcadores |
| CustodyEngine | Delegação, retomada e retorno dos recibos |
| ChatRuntime | Coordenar caminhos e manter a validação final |
| NearbyTransport | Continuar responsável pelo enlace; não decidir orçamento de custódia |
| RastroService | Coordenar execução, conectividade e ciclos |
| CustodyActivity | Exibir responsabilidades e transferências sem expor texto |
| ChatActivity | Preservar estados corretos de entrega |

Separar classes adicionais se a evolução tornar o mecanismo de custódia grande demais. Evitar concentrar banco, criptografia, roteamento e interface na Activity.

Negociar capacidades antes de enviar o novo formato. Pares antigos continuam operando apenas os fluxos suportados; não reduzir proteção ou descartar metadados para forçar compatibilidade.

## 13. Interface

Preservar os estilos e componentes atuais.

Na conversa, apresentar estados locais verificáveis:

- Salva, aguardando oportunidade.
- Repasse em confirmação.
- Em transporte por portadores.
- Entrega confirmada.
- Expirada sem confirmação.

Não apresentar “chegando”, percentual de trajeto, localização atual ou total global de cópias como fatos conhecidos.

Na caixa de correspondência, mostrar pacote, papel local, quantidade local de autorizações, prazo restante e eventual transferência pendente. Explicar que uma autorização bloqueada aguarda reconciliação, e não representa necessariamente falha definitiva.

Manter remoção local com confirmação. Informar sua consequência para entrega/recibo e esclarecer que ela não cancela cópias remotas nem restaura orçamento.

Erros de validação não devem vazar conteúdo privado ou chaves para logs.

## 14. Ordem recomendada para implementação

### Parte 1 — Protocolo, persistência e repasse seguro

1. Consolidar as invariantes e os testes que não podem regredir.
2. Fixar o formato versionado, a política de duas autorizações e os limites.
3. Implementar validação da origem e das delegações sem cadastro nos portadores.
4. Criar a migração e os estados persistentes de transferência.
5. Implementar consulta, bloqueio anterior ao envio, aceite durável e retomada.
6. Permitir cadeias de repasse, com deduplicação, cotas, prazo e limite de saltos.
7. Preservar o fluxo antigo para mensagens e aparelhos sem suporte.
8. Validar perdas e reinícios em cada ponto do protocolo.

**Critério:** A → B → D → C funciona com A indisponível e intermediários sem contatos. B não reaproveita uma autorização após uma aceitação incerta. C salva uma única mensagem válida.

### Parte 2 — Retorno, coordenação de caminhos e interface

1. Implementar a prova verificável de entrega e os recibos transportáveis.
2. Aplicar orçamento, reserva e deduplicação também ao retorno.
3. Coordenar entrega direta, malha conectada e transporte persistente.
4. Implementar limpeza autenticada e retenção dos marcadores.
5. Atualizar os estados e explicações da interface.
6. Executar regressões de chat, SOS, mapa, permissões e navegação.
7. Realizar ensaios em aparelhos físicos com encontros separados.

**Critério:** a confirmação de C chega a A por um caminho disponível, sem depender da cadeia inversa completa. Apenas esse recibo autenticado confirma a conversa. Interrupções não renovam orçamento nem duplicam mensagens.

A funcionalidade não deve ser apresentada como concluída apenas porque o texto chegou a C: retorno de confirmação, compatibilidade e recuperação fazem parte da entrega inicial.

## 15. Testes necessários

### Protocolo e segurança funcional

- Intermediários sem contatos transportam mensagem e recibo.
- Origem, destino, hash, política ou assinatura adulterados são rejeitados.
- Uma identidade não assume autorização de outra.
- Cadeia quebrada, excessiva, repetida ou com ciclo é rejeitada.
- Apresentação de sessão antiga não autoriza uma nova transferência.
- Um portador não consegue produzir confirmação final de C.
- Um recibo de outra mensagem não confirma nem limpa a mensagem em teste.

### Persistência e falhas

- Crash antes e depois do bloqueio no cedente.
- Crash antes e depois da gravação no receptor.
- Perda da oferta, do aceite e da resposta à consulta.
- Repetições com o mesmo ID são idempotentes; IDs conflitantes são rejeitados.
- Reinício dos dois aparelhos em ordens diferentes.
- Remoção local durante transferência pendente sem reemissão de orçamento.
- Espaço insuficiente e falha de transação sem confirmação indevida.
- Expiração, mudança de relógio, marcadores e retomada tardia.

### Entrega e orçamento

- Duas linhas em caminhos distintos chegam sem duplicar o histórico.
- Tentativa concorrente de delegar a mesma linha não produz duas saídas válidas.
- Destino alcançado diretamente enquanto há cópia em trânsito.
- Portador aproveita malha conectada sem criar custódias implícitas.
- Retorno por caminho diferente e recibo perdido/retransmitido.
- Limites de saltos, cotas por origem e orçamento global.
- Nenhuma passagem por outro portador renova prazo ou autorizações.

### Compatibilidade e aparelho

- Migração preserva banco, identidade e custódias anteriores.
- Pares antigos continuam no fluxo compatível.
- Encontros separados com pelo menos quatro aparelhos.
- Mais aparelhos ou simulação adicional para ramificações independentes.
- Tela bloqueada, serviço ativo, permissões, reinício e bateria.
- Gestos/navegação, mapa, SOS e conversas existentes continuam funcionando.

Testes locais de lógica não substituem ensaios reais de rádio, avaliação de bateria ou revisão criptográfica do protocolo.

## 16. Features futuras

| Feature | Objetivo e condições |
| --- | --- |
| Seleção inteligente de portadores | Considerar frequência e recência de encontros, capacidade e custo; não tratar previsão como garantia |
| Ajuste do orçamento | Comparar latência, probabilidade de entrega e consumo antes de ampliar autorizações |
| Redistribuição/cancelamento seguro | Recuperar autorizações somente com protocolo verificável de revogação e reconciliação |
| Novos transportes | Avaliar rede Wi-Fi local sem internet, Wi-Fi Direct, Wi-Fi Aware e Bluetooth conforme hardware, permissões e compatibilidade |
| Pontos de retransmissão | Dispositivos fixos ou dedicados que armazenem pacotes sem servidor de internet obrigatório |
| Transferência manual de pacotes | Avaliar exportação/importação cifrada mantendo IDs, validade, orçamento e deduplicação |
| Anexos e fragmentação | Rever capacidade, retomada, tamanho e custos de transporte |
| Privacidade de metadados | Reduzir exposição de relações, inventários e provas de entrega |
| Proteção criptográfica avançada | Revisão especializada, rotação de chaves e avaliação de sigilo futuro |
| Diagnóstico de entrega | Indicadores locais úteis, sem inventar localização ou conhecimento global da rede |

Nearby já administra tecnologias de conectividade próximas; avaliar o que uma integração adicional realmente acrescenta antes de criar caminhos redundantes.

Novos transportes devem entrar por uma interface de enlace comum e reutilizar o mesmo protocolo de mensagem, autorizações e deduplicação. Uma tentativa por Bluetooth e outra por Wi-Fi não pode criar dois orçamentos para a mesma linha.

A elegibilidade e a seleção de portadores também devem ser separadas: primeiro verificar se o par pode receber; depois decidir se é uma boa escolha. Isso permite evoluir o algoritmo sem reescrever a proteção ou o armazenamento.

## 17. Resultado esperado

O Rastro passa a transportar mensagens cifradas por uma sequência de aparelhos Android, usando conexões disponíveis e encontros ao longo do tempo, sem exigir contatos nos intermediários ou internet para a comunicação.

A primeira entrega mantém poucas autorizações, responsabilidades persistentes e comportamento explícito diante de incerteza. A base fica preparada para melhorar a seleção de portadores e acrescentar transportes sem abandonar os recursos existentes.
