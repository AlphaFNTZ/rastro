# Proposta de trabalho — Grupo Rastro
- **Integrantes:** Juliano Cesar Ferreira Ramos, Andre Bueno Ocanha, João Paulo Silva
- **Data:** 30/08/2026
- **Domínio escolhido:** Segurança e comunicação descentralizada de trabalhadores em áreas remotas (Rede Mesh com Detecção de Quedas).

## 1. O problema
Exploradores e trabalhadores em áreas remotas operam frequentemente sem cobertura de redes móveis. Em caso de acidentes, como quedas bruscas, a inviabilidade de enviar pedidos de socorro gera atrasos fatais no resgate. Atualmente, a solução de mercado restringe-se a rádios comunicadores caros, de alcance dependente de linha de visada, ou o uso de hardware de satélite pesado e custoso, com alertas que dependem de acionamento manual do usuário (o que falha se ele estiver inconsciente).

## 2. O que é observado
- **Ativo ou processo:** Explorador/Trabalhador em deslocamento em terreno remoto.
- **Grandeza física medida:** Aceleração (3 eixos, $m/s^2$) para detecção de movimento/queda e Posição para localização (GNSS).
- **Sensor e origem da incerteza:** Acelerômetro IMU (ruído e viés intrínseco do hardware) e GNSS (acurácia de posição e velocidade em metros e $m/s$ informadas pelo próprio sensor).
- **Período de amostragem pretendido e prazo associado:** Amostragem periódica a cada 50ms (20Hz) com prazo restrito na ordem de milissegundos para validação na Camada 1.

## 3. Mapeamento das dez capacidades

| # | Capacidade | O que significa no nosso domínio | Teste de aceitação |
|---|---|---|---|
| 1 | Domínio, contratos e estruturas | Estruturas imutáveis para Posição, Aceleração (IMURead) e Trajeto. Validação da acurácia e timestamps. | O modelo rejeita instâncias com acurácia inválida e devolve recorte temporal do trajeto em tempo linear e memória constante. |
| 2 | Incerteza e reconciliação | Covariância derivada da acurácia reportada pela API do GPS e do IMU. Filtro de erro grosseiro para descartar pulos de sinal GPS. | O resíduo diminui em três janelas reais. Injeção de erro grosseiro (GPS saltando) é detectado, e reconciliar sem o detector piora o resultado. |
| 3 | Concorrência e região crítica | Fila circular (*buffer*) protegida conectando a thread de leitura do acelerômetro com a thread de persistência e IA. | Dez execuções do teste de corrida (multithread) sobre o buffer sem falha. Retorno de cópias defensivas, nunca da referência. |
| 4 | Aquisição periódica em tempo real | Aplicativo rodando em *Foreground Service* extraindo GPS e IMU sem ser morto pelo OS. | Ao vivo: 30 minutos contínuos de aquisição com a tela bloqueada, registrando aceleração e atualizando o contador de pontos. |
| 5 | Ensaio reprodutível e injeção de falhas | Gravação e reprodução offline de trilhas e movimentos simulando quedas para testar a detecção sem depender da rua. | Reprodução determinística do trajeto com semente fixa. Tabela de sensibilidade mostrando em que nível de ruído o SOS falha. |
| 6 | Instrumentação e escalonabilidade | Medição exata de tempo de CPU ($C_i$) usado para ler IMU e fazer a inferência. | Tabela de tarefas com $C_i$ e dispersão medidos, jitter, prazo calculado e conclusão matemática de escalonabilidade (*Rate Monotonic*). |
| 7 | Aprendizado online sob memória constante | IA de borda detecta o envelope normal de locomoção (padrão de marcha) sem explodir a RAM. | A predição do modelo é validada fora dos dados de treino em dados retidos, respeitando memória constante. |
| 8 | Agente e modelo substituível | Agente avalia o risco da aceleração (IA) e decide retransmitir (Mesh) o Alerta de Socorro (SOS). | *Degradação graciosa*: Com o modelo IA removido/travado, o `RuleEngine` emite o SOS com base puramente no limiar do acelerômetro (física). |
| 9 | Criptografia e frota multi-nó | Alertas cifrados localmente antes de pular nós (Wi-Fi Direct/Ponto de Acesso). Sincronização de relógios dos exploradores. | Dado sensível cifrado com IV dinâmico e chave no *Keystore*. Ao menos 2 nós reconciliam o relógio e fundem 2 avistamentos do alerta num único evento. |
| 10| Validação cega, TRL e Lei de Amdahl | Medir o ganho de usar um modelo complexo de inferência contra a regra básica de limiar no acelerômetro. | Ensaio cego comparando os motores, com gráfico Amdahl de 5 repetições e dispersão reportando o speedup. Justificativa do TRL. |

## 4. Dados
- **Como serão obtidos:** Gravações do próprio aplicativo realizadas pelos integrantes, mesclando trechos normais de caminhada e simulações físicas de quedas bruscas.
- **Quantos dias ou quantas execuções:** 5 a 7 dias de coletas segmentadas por cada integrante.
- **Como a separação treino e validação será congelada:** Um percentual dos trajetos e simulações (coletados no último dia) ficará retido em pasta separada no repositório, não sendo processado até a semana 7 para evitar vazamento de dados (*data leakage*).

## 5. Riscos
| Risco | Probabilidade | Plano B |
|---|---|---|
| A API *Nearby* / *Wi-Fi Direct* barrar saltos automáticos em background (*multi-hop*). | Média | Usar rede ponta-a-ponta simples via Ponto de Acesso e HTTP nativo (NodeServer), simulando a estrutura da frota exigida na AV2 sem dependência do Nearby. |
| O tempo de inferência do modelo (*prefill*) estourar o prazo do ciclo de tempo real da Camada 1. | Média | Remover o modelo da malha crítica de aquisição (executando-o em background isolado) e depender do *RuleEngine* para respostas ultrarrápidas. |
| Bloqueio rigoroso de *Foreground Service* em ROMs de fabricantes (ex: Xiaomi/Motorola). | Alta | Declarar `foregroundServiceType` explicitamente, testar exceções de bateria (Doze) e demonstrar no aparelho de quem tiver o Android mais "limpo". |

## Parecer do docente   
- [ ] Aprovada 
- [ ] Aprovada com ressalva 
- [ ] A refazer
- **Observações:**
