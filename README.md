# 🛰️ Rastro — Nó de Borda & Rede Mesh Descentralizada

> **GAT108 — Automação Avançada | Universidade Federal de Lavras (UFLA)**  
> **Tema:** Inteligência Artificial de Borda para Sistemas Ciberfísicos  

---

## 📌 Visão Geral do Projeto

O **Rastro** é um sistema ciberfísico de borda projetado para garantir a segurança e a comunicação contínua de equipes trabalhando em áreas remotas sem cobertura de redes móveis ou internet. 

A solução transforma smartphones Android comuns em nós de uma rede mesh offline descentralizada (via Wi-Fi Direct / Bluetooth LE). O aplicativo executa processamento e inferência de IA inteiramente no dispositivo (*Edge AI*), detectando situações de emergência (como quedas bruscas) através dos sensores inerciais e propagando alertas de socorro em saltos (*multi-hop*) entre os nós até atingir a base de monitoramento.

---

## 🎯 Principais Funcionalidades

- **Comunicação Mesh Offline:** Envio e retransmissão de pacotes de dados e alertas entre nós sem necessidade de infraestrutura centralizada[cite: 1].
- **Detecção de Quedas na Borda:** Aquisição contínua de aceleração e inferência local para identificação de eventos críticos[cite: 1].
- **Operação em Segundo Plano:** Execução ininterrupta via serviço de primeiro plano, garantindo funcionamento mesmo com o aparelho bloqueado[cite: 1].
- **Degradação Graciosa:** Mecanismo de contingência baseado em motor determinístico de regras (RuleEngine) caso a inferência do modelo falhe ou seja removida[cite: 1].
- **Privacidade e Segurança:** Dados sensíveis cifrados em repouso com AES-GCM e chaves mantidas no Android Keystore, sem exportação de dados para a nuvem[cite: 1].

---

## 📁 Estrutura da Documentação

- [`docs/PROPOSAL.md`](docs/PROPOSAL.md) — Ficha formal da proposta do projeto com a Tabela de Mapeamento das Dez Capacidades[cite: 1].
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — Registro de Decisões de Arquitetura (ADR)[cite: 1].
- [`docs/presentations/`](docs/presentations/) — Slides das apresentações da disciplina[cite: 1].

---

## 👥 Integrantes do Grupo

- **Juliano Cesar Ferreira Ramos** — [@github_user1](https://github.com/AlphaFNTZ) — `juliano.ramos@estudante.ufla.br`
- **João Paulo Silva** — [@github_user2](https://github.com/user2) — `email2@ufla.br`
- **Andre Bueno Ocanha** — [@github_user3](https://github.com/user3) — `email3@ufla.br`

---

## 🛠️ Requisitos e Configuração do Ambiente

- **IDE:** Android Studio (JDK 17)[cite: 1]
- **Linguagem:** Java / Kotlin[cite: 1]
- **minSdk:** 26 (Android 8.0 Oreo)[cite: 1]
- **Testes:** Unidade em JUnit rodando localmente na JVM[cite: 1]