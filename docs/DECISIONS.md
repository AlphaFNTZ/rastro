# Registro de Decisões de Arquitetura (ADR)

## D-01 — Linguagem e Ecossistema
- **Data:** 30/08/2026
- **Decisão:** Kotlin como linguagem principal da nossa implementação, consumindo os contratos e testes originais que estão em Java.
- **Alternativas consideradas:** Usar 100% Java ou 100% Kotlin traduzindo o repositório base.
- **Motivo:** O Android atual é otimizado para Kotlin. O invólucro do motor local de IA e o Jetpack Compose fluem muito melhor com Kotlin.
- **Consequências:** Atenção redobrada com anotações de nulabilidade e `@JvmStatic` na hora de interligar as lógicas em Kotlin com os testes originais do professor escritos em Java.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-02 — Concorrência e Tempo Real
- **Data:** 30/08/2026
- **Decisão:** Uso de `Thread` e `ExecutorService` na Camada 1 (Aquisição) e *Corrotinas* na Camada 3 (Agente).
- **Alternativas consideradas:** Corrotinas em tudo; Threads em tudo.
- **Motivo:** A Camada 1 exige um-para-um (tarefa/thread) visível para o cálculo de Rate Monotonic cobrado na tabela de escalonabilidade da Fase II. A Camada 3 ganha com o cancelamento estruturado e o `withTimeout` do Kotlin.
- **Consequências:** O relatório da AV2 terá que mapear explicitamente a Thread alocada na Camada 1 e indicar qual despachante as corrotinas usam na Camada 3 para análise de prioridades.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-03 — Persistência dos Dados Sensíveis
- **Data:** 30/08/2026
- **Decisão:** `SQLiteOpenHelper` com os campos sensíveis cifrados individualmente.
- **Alternativas consideradas:** Room (ORM) ou Arquivo anexo simples.
- **Motivo:** Evitar verbosidade de bibliotecas gigantes. Permite ver exatamente a *query* gerada e atende bem ao requisito de cifrar apenas colunas específicas via `AesGcmVault` implementado por nós, sem delegar a segurança para o ORM (que tira os pontos da AV2).
- **Consequências:** O SQL não terá verificação de tipagem em tempo de compilação, exigindo cuidado para não cometer erros de digitação em nomes de colunas.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-04 — Divisão de Trabalho do Trio
- **Data:** 30/08/2026
- **Decisão:** Sistema de Rodízio Semanal (Codificador Primário, Secundário, e Revisor/Testador).
- **Alternativas consideradas:** Divisão por pacote/arquivo permanente.
- **Motivo:** A rubrica de contribuição exige que todo membro tenha *commits* rastreáveis em todas as semanas, avaliando o domínio completo na arguição. O modelo de divisão por arquivo faz com que um integrante não saiba explicar o código do outro na banca.
- **Consequências:** Exigirá forte disciplina no uso de ramos (*branches*) e *Pull Requests* para que todos compreendam 100% da lógica e tenham pontuação máxima.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-05 — Nível de API Alvo
- **Data:** 30/08/2026
- **Decisão:** `minSdk 26` (Android 8.0).
- **Alternativas consideradas:** `minSdk 24` ou `minSdk 29+`.
- **Motivo:** O Android 8.0 (API 26) garante suporte à leitura das acurácias da velocidade via sensor GPS (GNSS), que é o dado basilar usado pela matriz de covariância na reconciliação (Camada 2).
- **Consequências:** Aparelhos muito antigos serão excluídos, mas todos os integrantes do grupo possuem hardware igual ou superior à API 26.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-06 — Revisão de D-01: Kotlin puro, sem mistura com Java
- **Data:** 15/09/2026
- **Decisão:** A implementação do trio será 100% Kotlin, sem nenhum código de produção ou de teste em Java. As classes de domínio da Semana 1 (`DomainExceptions`, `IMURead`, `Posicao`, `Trajeto`) e seus testes JUnit já foram escritos inteiramente em Kotlin.
- **Alternativas consideradas:** Manter a redação original de D-01, que previa "consumir contratos e testes originais em Java" — herdada de uma leitura inicial do guia sobre o repositório de referência do Fragmented Learning.
- **Motivo:** Nosso domínio (segurança de trabalhador remoto com detecção de queda) é próprio do trio, não uma tradução do caso inspirador — não existe contrato ou teste de terceiros em Java para consumir. Escrever tudo em Kotlin desde o início evita por completo o atrito de interoperabilidade descrito na seção 6.2 do guia (`@JvmStatic`, `@Throws`, `@JvmOverloads`, tipos de plataforma nulos), simplesmente porque essa fronteira Java/Kotlin nunca existe no nosso repositório.
- **Consequências:** Nenhuma das armadilhas de interoperabilidade Java/Kotlin listadas no guia se aplica a este projeto. Se algum componente do pacote de *fakes* do professor vier a ser consumido e estiver em Java, esta decisão precisa ser revisitada com uma nova entrada.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-07 — Revisão de D-02: remoção de corrotinas, ExecutorService/Thread em todas as camadas
- **Data:** 15/09/2026
- **Decisão:** Substitui a parte de D-02 que previa corrotinas na Camada 3 (Agente). A partir de agora, `Thread` e `ExecutorService` (`java.util.concurrent`) são usados em **todas** as camadas de concorrência do sistema, incluindo o laço do Agente. Nenhuma corrotina Kotlin é usada em nenhuma camada.
- **Alternativas consideradas:** Manter corrotinas na Camada 3 conforme D-02 original, usando `withTimeoutOrNull` para o orçamento de latência do agente.
- **Motivo:** Um único modelo de concorrência em todo o repositório reduz o atrito de revisão entre os três integrantes e evita o mapeamento adicional que o guia exige de quem usa corrotinas — declarar explicitamente qual despachante cada corrotina usa e como isso muda a interferência entre tarefas (seção 6.4). Com os três dominando igualmente `Thread`/`ExecutorService`, a arguição fica defensável por qualquer integrante, não só por quem escreveu o código do agente.
- **Consequências:** O orçamento de latência do Agente (Camada 3) será implementado manualmente via `Future.get(timeout, TimeUnit)` em vez do `withTimeoutOrNull` de uma linha, perdendo o cancelamento estruturado das corrotinas. Fica registrado que — como o próprio guia observa na seção 6.4 — interromper uma inferência nativa já em andamento não funciona de verdade em nenhum dos dois modelos: `Future.cancel(true)` faz o laço do agente seguir adiante, mas não impede a inferência de continuar consumindo processador até terminar. Isso precisa constar no relatório da AV2, não ser apresentado como resolvido.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-08 — Atitude do IMU representada como escalar (inclinação), não pitch/roll/yaw completo
- **Data:** 15/09/2026
- **Decisão:** `IMURead` representa a atitude do aparelho como um único ângulo `inclinacaoGraus` (0°–180°, ângulo entre o eixo principal do aparelho e a vertical), em vez de pitch/roll/yaw completos ou um quatérnio.
- **Alternativas consideradas:** Atitude completa via pitch/roll/yaw (3 ângulos) ou quatérnio (4 componentes).
- **Motivo:** O `ThresholdDetector` da Camada 1 só precisa saber se o aparelho ficou plano (horizontal) depois de um pico de aceleração, para suspeitar de queda seguida de imobilidade — um escalar já carrega essa informação. Atitude completa adicionaria complexidade sem contribuir para o critério de aceitação da detecção de queda.
- **Consequências:** Se decidirmos refinar a detecção mais adiante (por exemplo, diferenciar queda para frente de queda lateral), o contrato de `IMURead` precisa mudar, não só a lógica do `ThresholdDetector` — isso deve ser tratado como nova decisão, não como extensão silenciosa do contrato.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-09 — Limite de sanidade da magnitude de aceleração do IMU
- **Data:** 15/09/2026
- **Decisão:** `IMURead` rejeita, na construção, leituras com módulo de aceleração em qualquer eixo acima de 200,0 m/s² (~20,4 g) — constante `ACELERACAO_MAX_MPS2`.
- **Alternativas consideradas:** Usar o fundo de escala do datasheet do acelerômetro específico de cada aparelho do trio; não impor limite algum na Camada 1 e delegar toda a plausibilidade ao `Reconciler`/`GrossErrorDetector` da Semana 2.
- **Motivo:** É preciso uma barreira de sanidade já no objeto de domínio (capacidade 1), antes de qualquer reconciliação estatística (capacidade 2). Como datasheets variam por aparelho, 200,0 é um valor de bom-senso de engenharia, não uma especificação de hardware.
- **Consequências:** Precisa ser recalibrado com dados reais dos acelerômetros dos três aparelhos assim que a coleta começar (Semana 2/3). Um valor mal calibrado pode rejeitar picos de queda genuínos (falso negativo na entrada do detector) ou deixar passar ruído absurdo de sensor (falso positivo).
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-10 — Velocidade média do Trajeto usa a velocidade reportada pelo sensor GNSS
- **Data:** 15/09/2026
- **Decisão:** `Trajeto.velocidadeMediaMps()` calcula a média aritmética do campo `velocidadeMps` que cada `Posicao` já traz do sensor GNSS, em vez de recalcular a velocidade por distância/tempo entre pontos consecutivos (haversine).
- **Alternativas consideradas:** Recalcular velocidade a partir da distância geodésica (haversine) entre pontos consecutivos e do intervalo de tempo entre eles.
- **Motivo:** Manter `Trajeto` como estrutura de dados simples (capacidade 1), sem acoplá-la a uma fórmula de distância geodésica que pertence mais naturalmente à camada de reconciliação (capacidade 2, `Reconciler`) da Semana 2.
- **Consequências:** A acurácia dessa média depende inteiramente da acurácia que o próprio sensor GNSS reporta (`acuraciaVelocidadeMps`). Se o `Reconciler` da Semana 2 vier a corrigir a velocidade por mínimos quadrados ponderados, `Trajeto` pode precisar de um método adicional que use a velocidade reconciliada, não a bruta.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-11 — Ordem cronológica como invariante estrutural de Trajeto
- **Data:** 15/09/2026
- **Decisão:** `Trajeto.comNovoPonto()` rejeita, com `InvalidTrajetoException`, qualquer `Posicao` cujo `timestampMs` seja anterior ao do último ponto já armazenado. Timestamps iguais são aceitos.
- **Alternativas consideradas:** Aceitar pontos fora de ordem e reordenar internamente a cada inserção; aceitar sem validação e deixar a responsabilidade inteiramente para quem chama.
- **Motivo:** Reordenar a cada inserção quebraria o custo esperado de `comNovoPonto` e a garantia de tempo linear/memória constante da capacidade 1. Falhar cedo com uma exceção específica é mais barato e mais seguro do que reordenar silenciosamente.
- **Consequências:** Empurra para a fonte de leitura (o futuro `GpsSource`/`FixSource` da Semana 4) a responsabilidade de nunca entregar leituras fora de ordem ao `Trajeto`. Se o sensor ou o SO entregar leituras atrasadas ou reordenadas, isso precisa ser filtrado antes de chegar aqui.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo

## D-12 — Trajeto não é data class
- **Data:** 15/09/2026
- **Decisão:** `Trajeto` é uma classe comum, com construtor privado e fábrica `vazio()`, e não uma `data class` como `Posicao` e `IMURead`.
- **Alternativas consideradas:** Declarar `Trajeto` como `data class`, igual às demais estruturas de domínio.
- **Motivo:** `Trajeto` tem comportamento (janela deslizante, recorte temporal) e identidade de estrutura — não é um valor comparável por igualdade de conteúdo como `Posicao`/`IMURead`. Os métodos de brinde de uma `data class` (`equals`/`hashCode`/`copy` por conteúdo) não fazem sentido semântico aqui e poderiam mascarar bugs: dois `Trajeto` com os mesmos pontos não são necessariamente "a mesma coisa" no sentido que importa para o sistema.
- **Consequências:** Nenhuma perda funcional; é decisão de modelagem a manter se novas estruturas com comportamento (como o futuro `CircularTrackBuffer` da Semana 4) forem criadas.
- **Decidido por:** Juliano Cesar, Andre Bueno, João Paulo
