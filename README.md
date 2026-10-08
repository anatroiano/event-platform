# 📅 Event Platform

Uma plataforma baseada em microsserviços para gerenciamento de eventos, desenvolvida com Java e Spring Boot, com
comunicação assíncrona entre os serviços utilizando RabbitMQ.

---

## ✨ Funcionalidades

- Criação e gerenciamento de eventos
- Inscrição de participantes em eventos
- Validação das regras de negócio para inscrição
- Processamento assíncrono de e-mails para confirmação de inscrição
- Documentação com Swagger/OpenAPI
- Testes automatizados, unitários e de integração

---

## 🛠️ Tecnologias utilizadas

- Java 21
- Spring Boot
- Spring Data JPA
- Spring Web MVC
- PostgreSQL (um banco de dados por serviço)
- RabbitMQ
- Maven
- Swagger/OpenAPI
- JUnit 5 + Mockito
- Testcontainers
- Docker
- Docker Compose

---

## 🏗️ Arquitetura

O projeto adota uma arquitetura de microsserviços com comunicação orientada a eventos. O fluxo de comunicação entre os
serviços acontece de maneira assíncrona utilizando o RabbitMQ para envio e recebimento de mensagens.

### 🧩 Serviços

1. **`events-service`**: Microsserviço responsável por fornecer a API REST para as operações do domínio de eventos. Ao
   processar requisições, ele publica mensagens no RabbitMQ para notificar outros serviços.
2. **`email-service`**: Microsserviço responsável por consumir as mensagens publicadas pelo events-service no RabbitMQ e
   processar o envio de e-mails de confirmação de inscrição.

### 📐 Diagrama da arquitetura

```mermaid
graph TD
    Client["Cliente / Frontend"]
    Producer["events-service<br/>Producer"]

    subgraph Rabbit["RabbitMQ"]
        Exchange["notifications.exchange<br/>(Topic Exchange)"]
        Queue["email.notifications.queue"]
        DLX["notifications.exchange.dlx<br/>(Dead Letter Exchange)"]
        DLQ["email.notifications.dlq<br/>(Dead Letter Queue)"]
    end

    Consumer["email-service<br/>Consumer"]

    Client -->|"HTTP / REST"| Producer
    Producer -->|"Publica"| Exchange
    Exchange -->|"Routing: email.#"| Queue
    Queue -->|"Consome"| Consumer

    Queue -.->|"Falha após retry"| DLX
    DLX -->|"Mensagem rejeitada"| DLQ

    classDef client fill:#f5f5f5,stroke:#888,color:#1a1a1a
    classDef service fill:#e8f0fe,stroke:#4a6fa5,color:#1a1a1a
    classDef broker fill:#fff4e0,stroke:#c98a1b,color:#1a1a1a
    classDef dead fill:#fde8e8,stroke:#c0392b,color:#1a1a1a

    class Client client
    class Producer,Consumer service
    class Exchange,Queue broker
    class DLX,DLQ dead
    end
```

---

## 🐳 Como executar

### Pré-requisitos

* Docker
* Docker Compose

### Configuração

O projeto contém arquivos de exemplo para variáveis de ambiente. Nos diretórios de cada serviço, você poderá encontrar
um arquivo de exemplo (`.env-example`).

Copie os arquivos de exemplo para `.env` em suas respectivas pastas e ajuste os valores:

```bash
cp email-service/.env-example email-service/.env
cp events-service/.env-example events-service/.env
```

### Execução

Para iniciar toda a infraestrutura simultaneamente usando o Docker Compose de produção, na raiz do projeto
execute:

```bash
docker compose up --build
```

> O comando criará os bancos de dados PostgreSQL, o RabbitMQ e fará o build das imagens dos microsserviços. Utilize o
`--build` sempre que alterar o código-fonte, o `pom.xml` ou os `Dockerfile`s. Nas demais execuções, utilize apenas
`docker compose up`.

Para utilizar o ambiente de desenvolvimento:

```bash
docker compose -f docker-compose.dev.yml up --build
```

> Utilize `--build` na primeira execução. Depois, utilize apenas `docker compose -f docker-compose.dev.yml up`.

### Acessando os serviços (Ambiente Dev)

- **`events-service`**: `http://localhost:8081`
- **`email-service`**: `http://localhost:8082`
- **RabbitMQ Management**: `http://localhost:15672`
    - *As credenciais padrão (guest/guest) podem ser vistas no arquivo `docker-compose.dev.yml`.*

### Executando serviços específicos

Também é possível iniciar apenas os serviços necessários para um determinado microsserviço.

Por exemplo, para executar apenas o `events-service`, juntamente com seu banco de dados e o RabbitMQ:

```bash
docker compose up events-api events-db rabbitmq --build
```

---

## 📚 Documentação da API (Swagger / OpenAPI)

Com os serviços em execução, acesse a documentação interativa no seguinte endereço:

- **Events Service**: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)

---

## 👩‍💻 Autora

Ana Carolina Troiano
