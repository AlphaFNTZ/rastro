# Semana 2 — incerteza, região crítica e comunicação direta

## Estado da entrega

Implementados no projeto da **raiz**, módulo `app/src`:

- Covariância GNSS a partir de `Location.accuracy` e `speedAccuracyMetersPerSecond`,
  verificando presença e validade. Informação ausente não vira zero.
- Incerteza IMU explícita: categoria de qualidade preservada, sigma indisponível
  na API padrão. Esse item da rubrica depende de solução alinhada com o professor.
- Buffer circular protegido, snapshots defensivos, drenagem atômica, perdas contadas.
- Aquisição bruta do acelerômetro a 50 Hz solicitados; frequência observada na tela.
- Descoberta Wi-Fi Direct, conexão TCP, SOS manual, confirmação e timeout.
- Testes JVM de domínio, dez rodadas concorrentes e troca TCP em loopback.
- Testes instrumentados do mapeamento `Location` (precisam de dispositivo/emulador).

**Ainda não implementados:** malha de três nós, roteamento/retransmissão, detecção de
quedas, reconciliação/filtro de erro grosseiro, IA, persistência cifrada e execução
com tela bloqueada. Campos de protocolo não comprovam multi-hop. Dez rodadas sem
falhas são evidência empírica; o lock é que protege as operações compostas.

## Onde está cada parte

| Pacote | Responsabilidade |
|---|---|
| `com.example.rastro.s2` | Covariância, leitura IMU imutável e buffer |
| `com.example.rastro.sensors` | Adaptadores Android GNSS e acelerômetro |
| `com.example.rastro.network` | Contrato de transporte, protocolo v1, TCP e Wi-Fi Direct |
| `MainActivity` | Permissões, demonstração, ACK e métricas temporárias |

O consumidor drena lotes para calcular métricas. Não existe ainda persistidor ou
agente. Sua integração futura deve receber o lote após a saída do lock. Por padrão
a tela usa capacidade 500 (cerca de 10 s a 50 Hz), drenagem a cada segundo, com
descarte do mais antigo em sobrecarga. Não há garantia de retenção de todas as amostras.

## Modelo de incerteza

`R = diag(r68² / (-2 ln(0,32)), r68² / (-2 ln(0,32)), acuraciaVelocidade²)`.

É a covariância de observação para `[leste em m, norte em m, velocidade escalar em m/s]`.
O erro horizontal é assumido gaussiano isotrópico, e as correlações são assumidas
nulas. A aproximação de 68% escalar por um sigma é explícita. Esta etapa não faz
projeção geográfica, filtro Kalman nem atualização de estado. Não use a matriz em
latitude/longitude diretamente. Acurácia zero reportada é preservada; um filtro
posterior deve tratar singularidade, sem confundir zero com ausência de informação.

`SensorEvent.accuracy` não fornece sigma por eixo. `IncertezaImu.Reportada` existe
para fontes quantitativas reais; o adaptador padrão nunca cria esse estado. Testes
usam valores sintéticos identificados, não evidência de coleta física. A aceleração
bruta inclui gravidade. Não é calculada inclinação dinâmica nessa entrega.

## Build e testes

Abra a pasta que contém este `docs/`, não a pasta `app/`. Use SDK Platform 37
(identificador de instalação `platforms;android-37.0`) e as
ferramentas pedidas pelo AGP. O wrapper usa Gradle 9.6.0, AGP 9.4.0 e daemon JDK 25;
Java 11 é o alvo de bytecode, não a versão do daemon. Configure o SDK no Android
Studio ou em `local.properties` (não versionado) e `JAVA_HOME` para um JDK adequado.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest --tests '*CircularImuBufferConcurrencyTest' --rerun-tasks
.\gradlew.bat :app:connectedDebugAndroidTest
```

O teste parametrizado tem dez casos nomeados `rodada 1/10` a `rodada 10/10`, cada um
com novo buffer e executor, produtor, consumidor e observador, início coordenado,
timeouts e propagação das exceções com `Future.get`. Verifica ordem, integridade,
ausência de duplicações e `consumidas + descartadas = produzidas`. Observador e
consumidor alteram diretamente as coleções retornadas, sem criar cópias no teste.

Relatórios JVM: `app/build/reports/tests/testDebugUnitTest/index.html`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
Resultados efetivamente executados ficam em [VALIDACAO_S2.md](VALIDACAO_S2.md).

## Ensaio com dois aparelhos (executar pelo grupo)

1. Instale o mesmo APK em dois Androids com suporte a Wi-Fi Direct. Registre modelo,
   versão Android e commit. Desative dados móveis; mantenha Wi-Fi e localização
   ligados. Nenhum ponto de acesso com internet é necessário.
2. Abra o app nos dois e toque **Buscar dispositivos**. Conceda permissões. No
   Android 13+, dispositivos próximos; no 17+, rede local; nas versões antigas,
   localização precisa. Localização GNSS é pedida separadamente quando usada.
3. Escolha o outro aparelho na lista em apenas um dos celulares e aceite a conexão
   no outro. A lista pode conter dispositivos que não executam Rastro.
4. Aguarde **Canal pronto para SOS** nos dois. Envie um SOS de teste. Registre o ID,
   recebimento remoto e tempo até a confirmação. Inverta emissor/receptor.
5. Repita dez vezes, registre todas as falhas, desconecte e reconecte. Se o canal
   fechar, use **Desconectar** antes de buscar novamente. Não há reconexão automática
   nem reenvio durável; após 10 s sem ACK o usuário deve tentar novamente.
6. Teste negação de permissões, Wi-Fi desligado e saída da tela. Nenhum SOS deve
   aparecer como confirmado apenas por ter sido colocado na fila de saída.
7. Opcionalmente inicie IMU e GNSS. Para GNSS, use céu aberto; velocidade/acurácia
   podem estar ausentes em algumas leituras. Registre essa condição sem inventar dados.

| Tentativa | A/B, Android | ID SOS | Recebido | Confirmado | Tempo (ms) | Falha/observação |
|---|---|---|---|---|---|---|
| preencher após ensaio | | | | | | |

Mantenha a tela aberta. Sair/rotacionar encerra a sessão; esta prova não substitui
um serviço de primeiro plano. A conexão aceita um par; não conectar três celulares
ao mesmo grupo e apresentar isso como malha. UUIDs identificam mensagens, não
autenticam remetentes. Não há cifragem/autenticação no protocolo da aplicação;
usar só SOS de teste, sem payload de localização ou informações pessoais.

## Próximo marco e revisão

O ensaio posterior A → B → C precisa impedir caminho direto A → C e comprovar
retransmissão por B. Exigirá estratégia de topologia compatível com os aparelhos,
controle de TTL, deduplicação, entrega pendente e segurança. Não é parte validada da S2.

O parceiro deve revisar os contratos e executar os testes, conforme rodízio D-04.
Use [PR_SEMANA2.md](PR_SEMANA2.md) como descrição do PR. A criação/publicação remota e
a revisão humana não são presumidas por este documento.

## Fontes técnicas

- [Location: raio 68%, presença e acurácia de velocidade](https://developer.android.com/reference/android/location/Location)
- [SensorEvent: qualidade e timestamp monotônico](https://developer.android.com/reference/android/hardware/SensorEvent)
- [SensorManager: período solicitado é indicativo](https://developer.android.com/reference/android/hardware/SensorManager)
- [Wi-Fi Direct: descoberta, permissões e sockets](https://developer.android.com/develop/connectivity/wifi/wifip2p)
- [Rede local em Android 17](https://developer.android.com/privacy-and-security/local-network-permission)
