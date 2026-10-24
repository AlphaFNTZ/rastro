# Perfis de alcance: procedimento e formato

Nenhum raio real foi medido nesta entrega. O aplicativo não vem com perfil de
cobertura. Este procedimento permite produzir dados para a camada opcional.

## Ensaio de enlace direto

1. Use dois aparelhos atualizados, identifique fabricante/modelo de ambos e impeça
   retransmissão por terceiros. Confirme enlace direto na lista de vizinhos.
2. Registre ambiente, terreno, obstáculos, orientação/altura dos aparelhos, data,
   operador, localização da área e documento de evidência. Não use a precisão GPS
   como medida do alcance. Meça a distância por método adequado e registre incerteza.
3. Defina antes do ensaio um prazo de ACK (até 60 s), taxa mínima (90–100%) e ao
   menos dez repetições por distância. O SOS do app usa timeout de 10 s; para coletar
   suas latências, adote um critério compatível com essa janela.
4. Aumente a distância em etapas, registre todo envio, ACK local em ms ou falha.
   Não descarte falhas nem substitua timeout por latência zero. Aguarde a conclusão
   de cada tentativa antes da seguinte e registre ID do SOS para auditoria.
5. Repita com os modelos/obstáculos/terrenos/orientações de operação. Produza perfis
   separados para condições distintas. Mantenha as medições originais auditáveis.
6. O aplicativo agrupa repetições por distância exata em metros. A distância máxima
   aprovada antes do primeiro grupo insuficiente/reprovado vira o raio estimado.
   Esse critério conservador ainda não demonstra cobertura uniforme em todas as direções.

Tabela sugerida para coleta:

```csv
data_utc,modelo_a,modelo_b,ambiente,terreno,obstaculos,orientacao,distancia_m,sos_id,ack_ms,resultado,observacoes
```

## JSON para importação

Arquivo UTF-8 de até 64 KiB, `versao: 1`, `enlaceDireto: true`, de 10 a 1000 amostras.
Cada amostra contém `distanciaMetros` (positivo) e `latenciaAckMs` (inteiro ou `null`
para falha). Os campos descritivos são obrigatórios, até 500 caracteres cada.

Modelo **incompleto**, que deve ser preenchido com medições reais antes da importação:

```json
{
  "versao": 1,
  "enlaceDireto": true,
  "nome": "Nome do ensaio",
  "modeloA": "Fabricante e modelo A",
  "modeloB": "Fabricante e modelo B",
  "ambiente": "Condições observadas",
  "terreno": "Descrição do terreno",
  "obstaculos": "Obstáculos observados",
  "orientacao": "Orientação e altura dos dois aparelhos",
  "evidencia": "Referência ao registro integral das medições",
  "medidoEmEpochMs": 0,
  "timeoutAckMs": 10000,
  "taxaMinima": 0.9,
  "repeticoesMinimas": 10,
  "amostras": []
}
```

Substitua `medidoEmEpochMs` pelo horário UTC real e preencha `amostras` com os
registros, incluindo falhas. Campos numéricos inválidos, ausência de evidência,
medições indiretas e perfis sem distância aprovada são rejeitados. Não importe
dados sintéticos como se fossem calibração real.

Na tela, use Importar perfil de ensaio. Para habilitar Alcance estimado, confirme
que o par de modelos e as condições são aplicáveis ao SOS. A camada tem centro na
posição histórica do SOS; a origem pode ter se movido ou desconectado. Um novo SOS
exige nova confirmação. A qualidade baixa/média/alta do Nearby não participa do cálculo.
