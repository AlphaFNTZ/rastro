# S2: incerteza GNSS, buffer concorrente e SOS direto por Wi-Fi Direct

O app tinha apenas a tela inicial e os modelos da S1. Agora a tela permite descobrir
um segundo aparelho, conectar por Wi-Fi Direct/TCP e enviar um SOS manual com ACK
correlacionado e timeout. Aquisição IMU em thread própria alimenta um buffer circular
protegido; o consumidor apresenta métricas. GNSS fornece covariância derivada da
acurácia reportada. A ausência de sigma IMU é explícita, sem valores arbitrados.

## Validação

Ver [VALIDACAO_S2.md](VALIDACAO_S2.md) para resultados executados e pendências.
O teste de corrida contém dez rodadas independentes. Há testes de cópias defensivas,
covariância, formato de mensagens e troca TCP local. Testes instrumentados cobrem
o adaptador Location. Roteiro de ensaio físico em [README_Semana2.md](README_Semana2.md).

## Limites para revisão

- Transporte direto de dois aparelhos, sem retransmissão ou comprovação de malha.
- Sem serviço em segundo plano, detector de quedas, IA ou persistência sensível.
- Incerteza quantitativa IMU depende da fonte disponível e do alinhamento docente.
- Conferir permissões nos Androids dos integrantes e executar o ensaio real.
- Revisar D-06 a D-09; decisões de implementação ainda aguardam revisão do parceiro.

Revisor/testador: preencher no PR, conforme rodízio. Não houve revisão humana
presumida nem ensaio físico declarado automaticamente.
