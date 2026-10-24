# Telas XML — claro e escuro

Implementação baseada nas imagens e nas camadas CSS de `rastro-design/`.
As posições absolutas do CSS foram adaptadas para `dp`, `sp`, insets do sistema,
listas roláveis e dimensões flexíveis; as imagens não são fundos estáticos do app.

## Arquivos

- Principal: `app/src/main/res/layout/activity_main.xml`.
- Configurações: `app/src/main/res/layout/activity_configuracao.xml`.
- Dispositivos: `app/src/main/res/layout/activity_dispositivos.xml` e `item_dispositivo.xml`.
- Histórico: `app/src/main/res/layout/activity_historico.xml` e `item_historico.xml`.
- Navegação compartilhada: `app/src/main/res/layout/layout_bottom_nav.xml`.
- Estilos compartilhados: `app/src/main/res/values/ui.xml`.
- Paletas: `values/colors.xml`, `values-night/colors.xml` e os respectivos `themes.xml`.
- Fonte: Plus Jakarta Sans local, com licença em `assets/licenses/PlusJakartaSans-OFL.txt`.

Cada layout funciona nos dois modos através do tema DayNight já utilizado pelo
projeto. Não há duplicação de árvores XML em `layout-night`: apenas cores e temas
mudam automaticamente conforme o Android. A cartografia é escura em ambos,
conforme as referências. O mapa continua interativo, com cache, precisão e SOS.

## Comportamento preservado

- Principal: nome real, ativação/desativação, SOS, status, último SOS recebido,
  centralização local/remota e detalhes com legenda e atribuição.
- Configuração: alterar nome, limite de idade da posição recente, importar perfil
  validado, confirmar alcance para o SOS atual e gerenciar a região offline.
  Diagnóstico IMU/GNSS e licenças estão ao final da lista rolável.
- O texto do design “Tempo de atualização” mantém a função existente de configurar
  a validade de uma posição recente; não altera a frequência do receptor GPS.
- O alcance só é habilitado com perfil válido, SOS com coordenadas e confirmação
  das condições. Uma nova mensagem exige nova confirmação.
- Dispositivos: dados do serviço, busca por nome/identidade, enlace direto ou
  indireto; tocar revela UUID, estado e intermediário. O rádio efetivo não é
  inventado: continua “Nearby automático”, com explicação no detalhe.
- Histórico: eventos reais da sessão, recentes primeiro e agrupados por data local;
  tocar abre os detalhes completos, incluindo horários e informações do SOS.
- Navegação inferior seleciona a aba atual, mantém a principal como raiz e evita
  acumular instâncias das abas. Voltar das configurações preserva a principal;
  ações de mapa retornam a ela com a operação correspondente.
- Cada tela remove somente seu próprio observador do serviço e do mapa offline,
  evitando perder atualizações durante a sobreposição de ciclos de vida.

Os arquivos de referência nomeados “Ativado”/“Desativado” têm rótulos invertidos
em relação aos botões desenhados. No aplicativo, os botões seguem o estado real
do serviço: inativo → Ativar; ativo → Desativar e Enviar SOS.

## Validação

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

`TelasDesignTest` percorre as quatro telas nos dois temas, verifica os diálogos,
busca, detalhes, aba selecionada e retorno da configuração de mapa. Captura as
telas em `Android/data/com.example.rastro/files/telas-design` no emulador;
as cópias inspecionadas ficam em `app/build/telas-design/`.
Os vizinhos e eventos das capturas são fixtures exclusivas dos testes, nunca
adicionados ao serviço ou distribuídos como dados do aplicativo.

Os testes usam instrumentação nativa e acessibilidade: o Espresso instalado no
projeto usa uma API interna de entrada removida no emulador API 37.
`ObservadoresTelaTest` cobre a transição entre observadores de telas.
`MapaActivityTest` verifica recriação da principal; os testes de posição e mapa
offline existentes continuam incluídos. Comunicação Nearby entre aparelhos
físicos não é validada pelo emulador.
