# Rastro — Abrigo conectado

O abrigo aberto representa proteção; o nó central representa a pessoa/aparelho que mantém a comunicação em áreas remotas.

## Instalação no Android Studio
1. Faça uma cópia dos ícones existentes.
2. Copie o conteúdo de `android/app/src/main/res` deste pacote para `app/src/main/res` do seu projeto, aceitando a substituição dos arquivos com o mesmo nome.
3. No elemento `<application>` do AndroidManifest.xml, confira:

```xml
android:icon="@mipmap/icone"
android:roundIcon="@mipmap/icone_round"
```

4. Compile e instale o aplicativo. Se o launcher continuar exibindo o ícone antigo, reinicie o launcher/aparelho. Evite desinstalar se houver dados locais a preservar.

O pacote não substitui seus colors.xml. O fundo violeta está nos próprios assets. API 26+ usa os XMLs adaptativos; API 33+ também pode usar a camada monocromática quando o usuário habilita ícones temáticos. O sistema decide a máscara e a cor dos ícones temáticos.

| Densidade | Ícone comum/redondo | Camadas adaptativas |
|---|---:|---:|
| mdpi | 48 × 48 | 108 × 108 |
| hdpi | 72 × 72 | 162 × 162 |
| xhdpi | 96 × 96 | 216 × 216 |
| xxhdpi | 144 × 144 | 324 × 324 |
| xxxhdpi | 192 × 192 | 432 × 432 |

Cada pasta contém icone.webp, icone_round.webp, icone_background.webp e icone_foreground.webp. WebP sem perdas; foreground com transparência; background opaco e sem máscara. Existem dois XMLs em mipmap-anydpi-v26 e dois em mipmap-anydpi-v33, além de drawable/icone_monochrome.xml.

## Identidade
- Principal: #5E4AE3 (paleta clara fornecida).
- Alternativa sobre fundo escuro: #8B7CFF (paleta escura fornecida).
- Reverso: #FFFFFF. Preto: #000000.
- Ícone padrão: branco sobre violeta, consistente nos dois temas do app.
- Nome: DejaVu Sans Bold, convertido em contornos SVG; licença permissiva DejaVu/Bitstream Vera, incluída no pacote.
- Use SVG para a logo em interfaces/documentos; use os recursos Android para o launcher.
- Mantenha uma margem livre de pelo menos 1/8 da altura do símbolo. Não distorça, não acrescente sombras e não altere as proporções entre abrigo e nó.
- Para interfaces, prefira símbolo com pelo menos 24 px e lockup horizontal com pelo menos 140 px de largura; use apenas o símbolo em tamanhos pequenos.
- rastro-play-store-512.png é uma imagem quadrada opaca de 512 px, sem máscara pré-aplicada.

## Verificação
Os arquivos foram renderizados e inspecionados em recortes circular, quadrado arredondado e squircle; os tamanhos e referências XML foram verificados. O símbolo completo fica dentro da zona segura circular de 66dp das camadas de 108dp. O projeto Gradle não foi disponibilizado, portanto este pacote não foi compilado dentro do aplicativo.

Referência Android: https://developer.android.com/develop/ui/views/launch/icon_design_adaptive
