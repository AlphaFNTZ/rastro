# Evidências de validação — S3

Data: 24/09/2026. Validação automatizada nesta máquina, sem aparelhos conectados.

## Resultado automatizado

Comando executado:

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

- testes JVM: 68 testes, zero falhas;
- `assembleDebug`: sucesso;
- `lintDebug`: sucesso, zero erros e 16 avisos não bloqueantes;
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

Os quatro testes novos de `MeshRouter` cobrem entrega única, ACK, deduplicação,
redução de TTL, exclusão do enlace de entrada, destino local e ausência de vizinhos.
Outros quatro testes cobrem codificação do anúncio, Unicode, normalização, limite de
nome, entradas inválidas e diferenciação de nomes duplicados pelo UUID.
Dois testes adicionais cobrem serialização da presença, compatibilidade do protocolo
e incremento de saltos durante a propagação por um nó intermediário.
Nearby e o foreground service foram compilados contra o SDK 37, mas callbacks de
rádio e comportamento com tela bloqueada não podem ser comprovados na JVM.

## Pendências físicas

- advertising/discovery automático entre modelos Android distintos;
- conexões simultâneas e reconexão após perda de alcance;
- A → B → C sem enlace direto A → C;
- ACK multi-hop e latência em dez repetições;
- continuidade com tela bloqueada e impacto de bateria;
- comportamento após negação e posterior concessão de permissões.
