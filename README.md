# 🛰️ Rastro — Nó de Borda & Rede Mesh Descentralizada

> **GAT108 — Automação Avançada | Universidade Federal de Lavras (UFLA)**  
> **Tema:** Inteligência Artificial de Borda para Sistemas Ciberfísicos  

---

## 📌 Visão Geral do Projeto

O **Rastro** é um sistema ciberfísico de borda projetado para garantir a segurança e a comunicação contínua de equipes trabalhando em áreas remotas sem cobertura de redes móveis ou internet. 

**Estado atual (S3):** transporte offline automático com Nearby Connections
`P2P_CLUSTER`, múltiplos vizinhos, mensagens `BYTES`, roteamento por inundação com
TTL/deduplicação e ACK multi-hop. Cada nó possui nome amigável editável e UUID
estável; a tela distingue aparelhos descobertos, conectando e conectados, mostra a
qualidade dos enlaces diretos e identifica nós alcançáveis por intermediários. O
rádio físico (Bluetooth clássico, BLE ou Wi-Fi) é escolhido internamente pelo
Nearby e não é exposto por sua API. Rede e aquisição IMU podem permanecer ativas em
um serviço de primeiro plano após a tela ser fechada. Detecção de quedas, IA,
persistência cifrada, autenticação dos nós e validação física A → B → C ainda não
estão implementadas. A API padrão IMU não fornece sigma por eixo: a limitação
permanece explícita. Veja [entrega S3](docs/README_Semana3.md) e
[resultados da validação](docs/VALIDACAO_S3.md).

A solução proposta transformará smartphones Android comuns em nós de uma rede mesh offline descentralizada (via Wi-Fi Direct / Bluetooth LE). O objetivo é executar processamento e inferência de IA inteiramente no dispositivo (*Edge AI*), detectar situações de emergência através dos sensores inerciais e propagar alertas em saltos (*multi-hop*) até a base. A proposta detalhada prioriza regras determinísticas para quedas e IA para administrar a rede.

---

## 🎯 Funcionalidades planejadas

- **Comunicação Mesh Offline:** Envio e retransmissão de pacotes de dados e alertas entre nós sem necessidade de infraestrutura centralizada.
- **Detecção de Quedas na Borda:** Aquisição contínua de aceleração e inferência local para identificação de eventos críticos.
- **Operação em Segundo Plano:** Execução ininterrupta via serviço de primeiro plano, garantindo funcionamento mesmo com o aparelho bloqueado.
- **Degradação Graciosa:** Mecanismo de contingência baseado em motor determinístico de regras (RuleEngine) caso a inferência do modelo falhe ou seja removida.
- **Privacidade e Segurança:** Dados sensíveis cifrados em repouso com AES-GCM e chaves mantidas no Android Keystore, sem exportação de dados para a nuvem.

---

## 📁 Estrutura da Documentação

- [`docs/PROPOSAL.md`](docs/PROPOSAL.md) — Ficha formal da proposta do projeto com a Tabela de Mapeamento das Dez Capacidades.
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — Registro de Decisões de Arquitetura (ADR).
- [`docs/presentations/`](docs/presentations/) — Slides das apresentações da disciplina.

---

## 👥 Integrantes do Grupo

- **Juliano Cesar Ferreira Ramos** — [@AlphaFNTZ](https://github.com/AlphaFNTZ) — `juliano.ramos@estudante.ufla.br`
- **João Paulo Silva** — [@joaosilva86](https://github.com/joaosilva86) — `joao.silva86@estudante.ufla.br`
- **Andre Bueno Ocanha** — [@AndreOcanha](https://github.com/AndreOcanha) — `andre.ocanha@estudante.ufla.br`

---

## 🛠️ Requisitos e Configuração do Ambiente

- **IDE:** Android Studio com suporte ao AGP 9.4.0; abrir a raiz `rastro`, não `app/`
- **Build atual:** Gradle 9.6.0, AGP 9.3.3, daemon JDK 25, compileSdk/targetSdk 37; bytecode Java 11
- **Linguagem:** Java / Kotlin
- **minSdk:** 26 (Android 8.0 Oreo)
- **Testes:** Unidade em JUnit rodando localmente na JVM
- **S3:** `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
- **Aparelhos:** Nearby exige Google Play Services. A prova multi-hop exige três
  Androids e topologia que impeça o enlace direto A → C. A cópia histórica em
  `app/app` não contém esta entrega.
