# Proposta de trabalho — Grupo Rastro
- **Integrantes:** Juliano Cesar Ferreira Ramos, Andre Bueno Ocanha, João Paulo Silva
- **Data:** 30/08/2026
- **Domínio escolhido:** Segurança ciberfísica e comunicação descentralizada de trabalhadores remotos (Malha Mesh Otimizada por IA de Borda).

## 1. O problema
Trabalhadores em áreas remotas operam sem cobertura de redes móveis. Em caso de acidentes, pedidos de socorro dependem de rádios caros ou hardwares de satélite de acionamento manual (inúteis se a vítima estiver inconsciente). O diferencial técnico desta proposta é eliminar a dependência de infraestrutura externa criando uma rede mesh 100% offline (via conexões Ad-Hoc nativas) capaz de repassar o alerta entre dispositivos. Como o grande gargalo de redes descentralizadas é o esgotamento da bateria ao manter o rádio ativo, o sistema tratará o smartphone como uma planta física: uma IA de borda gerenciará dinamicamente a taxa de busca da rede cruzando dados de velocidade, bateria e isolamento para otimizar energia sem perder pacotes críticos de SOS.

## 2. O que é observado
- **Ativo ou processo:** Trabalhador em deslocamento em terreno remoto, interagindo com outros nós móveis.
- **Grandeza física medida:** Aceleração (3 eixos, m/s²) e Atitude (inclinação) para detecção determinística de queda, além de Posição/Velocidade (GNSS) para controle da malha de rede.
- **Sensor e origem da incerteza:** Acelerômetro e Giroscópio IMU (ruído de hardware e viés de integração) e GNSS (acurácia de posição e velocidade reportadas pelo sensor).
- **Período de amostragem pretendido e prazo associado:** Amostragem da cinemática a 20Hz (50ms) com prazo em milissegundos; atuador do rádio Wi-Fi reconfigurado dinamicamente na ordem de segundos.

## 3. Mapeamento das dez capacidades

| # | Capacidade | O que significa no nosso domínio | Teste de aceitação |
|---|---|---|---|
| 1 | Domínio, contratos e estruturas | Estruturas imutáveis para Posição, IMURead (Aceleração e Atitude) e Trajeto da rede. Validação de acurácia e restrições temporais. | O modelo rejeita construtores com acurácia inválida e devolve o histórico cinemático em tempo linear O(n). |
| 2 | Incerteza e reconciliação | Covariância oriunda da acurácia do GPS/IMU. Filtro de erro grosseiro para descartar saltos irreais de posição. | Resíduo cinemático diminui em 3 janelas de testes. O sistema detecta falha injetada no GNSS isolando a medição. |
| 3 | Concorrência e região crítica | Buffer circular conectando a aquisição de tempo real com o serviço em background que administra os pacotes mesh. | 10 execuções do teste de corrida (multithread) sem falha, provando que o buffer sempre retorna cópias defensivas. |
| 4 | Aquisição periódica em tempo real | *Foreground Service* sobrevivendo ao modo *Doze* do sistema operacional para monitorar a aceleração ininterruptamente. | Ao vivo: 30 min de aquisição sob tela bloqueada, validando a persistência do monitoramento. |
| 5 | Ensaio reprodutível e injeção de falhas | Gravação offline de perfis de caminhada variados e reprodução determinística para simular perdas de pacotes na malha. | Tabela documentando o ponto exato em que a taxa de ruído ou perda de pacotes colapsa o envio do SOS. |
| 6 | Instrumentação e escalonabilidade | Medição do tempo de CPU consumido pelo serviço de detecção e pelo reconfigurador de rádio. | Tabela com tempo medido, jitter e prazo. Conclusão matemática via *Rate Monotonic* de que as malhas não perdem o deadline. |
| 7 | Aprendizado online sob memória constante | IA aprende o padrão de velocidade, encontros de rede e consumo de bateria (estado de isolamento do usuário). | O modelo prevê a janela ideal de conexão em dados retidos, respeitando os limites da RAM (memória constante). |
| 8 | Agente e modelo substituível | IA avalia cinemática/bateria e usa a ferramenta de reconfigurar a rede para alterar o rádio (ação em malha fechada). | *Degradação graciosa*: Se o modelo for removido, o `RuleEngine` fixa a busca da rede em 10s e emite o SOS baseado em cinemática pura (pico de aceleração seguido de atitude horizontal). |
| 9 | Criptografia e frota multi-nó | Cifragem local de SOS via AES-GCM (chaves no *Keystore*). Sincronização temporal entre aparelhos para alinhar as janelas de escuta do rádio (*duty cycle*). | 2 ou mais aparelhos reconciliam a deriva do relógio e fundem o recebimento do mesmo SOS em alerta único. |
| 10| Validação cega, TRL e Lei de Amdahl | Medir a diferença de sucesso na entrega de pacotes e consumo energético entre a IA dinâmica e o `RuleEngine` estático. | Ensaio cego reportando a fração paralelizável (*speedup* de Amdahl em 5 repetições) da inferência e a viabilidade do TRL. |

## 4. Dados
- **Como serão obtidos:** Simulações com os celulares dos integrantes em velocidades distintas (a pé, correndo, em veículo) para capturar o tempo real de contato da rede mesh e leituras de acelerômetro simulando quedas.
- **Quantos dias:** 5 a 7 dias de coletas físicas cruzadas.
- **Congelamento:** Uma porção das coletas de rede e cinemática (último dia) ficará isolada no repositório para evitar *data leakage* (vazamento de dados) nos testes da Camada 7.

## 5. Riscos e Desafios de Engenharia
A complexidade reside em balancear estabilidade offline com física de redes (Descoberta vs. Energia):

| Risco | Probabilidade | Plano B / Mitigação Ciberfísica |
|---|---|---|
| Otimizar bateria falhar na entrega do SOS (janela de escuta do celular seguro ser maior que o tempo de contato físico do resgate). | Alta | A taxa de busca da rede não é fixa; o Agente a calcula com base na velocidade relativa (GNSS). Maior velocidade = taxa de busca maior. Acidentados transmitem em *broadcast* contínuo. |
| O sistema operacional (Xiaomi, Motorola) derrubar o *Foreground Service* ou bloquear o Wi-Fi Direct em segundo plano. | Alta | Empregar rede HTTP nativa ponta-a-ponta (NodeServer) sob `foregroundServiceType` declarado e validar nos aparelhos de Android mais "puro" da equipe. |
| Tempo de *prefill* do modelo LLM estourar a janela de malha crítica do acelerômetro. | Média | Isolar a predição da rede mesh em thread separada do núcleo de tempo real e depender do *RuleEngine* para a detecção de queda em milissegundos. |

## Parecer do docente
- [ ] Aprovada
- [ ] Aprovada com ressalva
- [ ] A refazer
- **Observações:**