# Semana 3 — Nearby, malha multi-hop e serviço em primeiro plano

## O que mudou

- `NearbyTransport` usa Nearby Connections e `P2P_CLUSTER`.
- Todos os nós anunciam e descobrem ao mesmo tempo.
- Conexões são solicitadas automaticamente; não há seleção de Wi-Fi ou de aparelho.
- Múltiplos endpoints podem permanecer conectados.
- Cada nó anuncia UUID estável e nome amigável editável.
- A tela lista aparelhos descobertos, conectando e conectados, com alcance direto,
  qualidade do enlace e nós alcançáveis por um ou mais intermediários.
- `MeshRouter` fornece TTL, deduplicação, retransmissão e ACK por flooding.
- Anúncios periódicos de presença permitem inferir rotas indiretas; elas expiram
  quando deixam de ser observadas.
- `RastroService` mantém transporte e IMU ativos com a tela fechada.
- A Activity tornou-se cliente do serviço e concentra somente permissões e UI.

Nearby 19.3.0 foi usado porque 19.5.0 contém metadados Kotlin 2.4 incompatíveis com
o compilador embutido no AGP 9.3.3 escolhido neste checkout. O projeto preserva esse
AGP e registra o recuo em D-10.

Nesta versão, `ACCESS_WIFI_STATE` e `CHANGE_WIFI_STATE` permanecem declaradas em
todas as APIs. Limitá-las com `maxSdkVersion=31` faz o Nearby 19.3 falhar no Android
13+ com `8032: MISSING_PERMISSION_ACCESS_WIFI_STATE`. São permissões normais do
manifesto e não geram uma solicitação adicional ao usuário.

## Transporte e alcance exibidos

O Nearby Connections escolhe automaticamente Bluetooth clássico, Bluetooth Low
Energy ou Wi-Fi e pode trocar de tecnologia durante a conexão. A API utilizada não
informa ao aplicativo qual rádio está ativo. Por isso a interface mostra
**Nearby automático (rádio físico não exposto)** em vez de adivinhar um transporte.
**Dispositivos próximos** é o grupo de permissões do Android que autoriza acesso às
tecnologias próximas; não é um quarto tipo de transporte.

Para enlaces diretos, a tela mostra a qualidade reportada pelo Nearby como baixa,
média, alta ou não informada. Para outros nós, o aplicativo propaga anúncios de
presença a cada cinco segundos e apresenta a quantidade de saltos e o próximo nó
direto. Uma rota desaparece depois de 16 segundos sem novo anúncio. Como o flooding
aceita a primeira cópia de cada anúncio, essa informação comprova um caminho
observado, mas não garante que ele seja o caminho mais curto nem que continue ativo
no instante seguinte.

## Limites de segurança

A conexão é aceita automaticamente sem comparar os dígitos de autenticação Nearby.
Isso atende somente à demonstração acadêmica com dados fictícios. Criptografia do
enlace não autentica a identidade declarada pelo nó nem fornece segurança ponta a
ponta para mensagens retransmitidas. Não usar localização pessoal ou SOS real.

## Como testar em três aparelhos

1. Instale o mesmo APK em A, B e C, todos com Google Play Services.
2. Conceda Bluetooth/dispositivos próximos, notificações e, se necessário, localização.
3. Toque **Iniciar Rastro** nos três. Não selecione rede ou aparelho.
   Antes ou depois de iniciar, defina nomes distintos em **Nome deste dispositivo**.
4. Organize a topologia para A alcançar somente B e C alcançar somente B. Distância
   por si só não é prova suficiente; confirme que A apresenta C como indireto via B
   e registre aparelhos, posições e enlaces exibidos.
5. Envie SOS de A. Verifique recebimento em B e C e ACK na origem.
6. Repita dez vezes, registrando ID, TTL, vizinhos, latência e falhas.
7. Bloqueie a tela e repita, confirmando a notificação persistente.

## O que ainda falta

- autenticação/pairing dos nós;
- persistência durável para store-and-forward;
- testes instrumentados do ciclo de vida do serviço e permissões;
- ensaio físico A → B → C;
- detecção de queda, IA, AES-GCM/Keystore e métricas energéticas.
