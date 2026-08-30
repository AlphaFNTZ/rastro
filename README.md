# 🛰️ Rastro — Nó de Borda & Rede Mesh Descentralizada

> **GAT108 — Automação Avançada | Universidade Federal de Lavras (UFLA)**  
> **Tema:** Inteligência Artificial de Borda para Sistemas Ciberfísicos  

---

## 📌 Visão Geral do Projeto

O **Rastro** é um sistema ciberfísico de borda projetado para garantir a segurança e a comunicação contínua de equipes trabalhando em áreas remotas sem cobertura de redes móveis ou internet. 

A solução transforma smartphones Android comuns em nós de uma rede mesh offline descentralizada (via Wi-Fi Direct / Bluetooth LE). O aplicativo executa processamento e inferência de IA inteiramente no dispositivo (*Edge AI*), detectando situações de emergência (como quedas bruscas) através dos sensores inerciais e propagando alertas de socorro em saltos (*multi-hop*) entre os nós até atingir a base de monitoramento.

---

## 🎯 Principais Funcionalidades

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

- **IDE:** Android Studio (JDK 17)
- **Linguagem:** Java / Kotlin
- **minSdk:** 26 (Android 8.0 Oreo)
- **Testes:** Unidade em JUnit rodando localmente na JVM
