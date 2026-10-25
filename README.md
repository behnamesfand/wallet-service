# Wallet Service

A RESTful wallet and ledger service built with Java and Spring Boot, designed to demonstrate reliable financial operations, transaction consistency, idempotency, and secure API access.

## Overview

Wallet Service provides APIs for managing wallet accounts and performing financial transactions, including deposits, withdrawals, and transfers. It uses a ledger-based approach to record financial operations and applies database transactions and locking to help maintain balance consistency.

## Features

- User registration and login
- JWT-based authentication and authorization
- Wallet account creation and balance retrieval
- Deposit and withdrawal operations
- Transfers between wallet accounts
- Ledger entry recording
- Transaction history with pagination and sorting
- Idempotency support for financial operations
- Account ownership validation
- Currency validation (currently supports IRR)
- Centralized exception handling
- Swagger/OpenAPI documentation
- Spring Boot Actuator health and monitoring endpoints
- Prometheus metrics endpoint

## Tech Stack

- **Java:** 21
- **Framework:** Spring Boot
- **Build Tool:** Maven
- **Database:** H2
- **Persistence:** Spring Data JPA / Hibernate
- **Security:** Spring Security and JWT
- **API Documentation:** Swagger / OpenAPI
- **Monitoring:** Spring Boot Actuator and Micrometer Prometheus

## Project Architecture

The project follows a feature-oriented package structure:

```text
com.project.wallet_service
├── account
├── transaction
├── ledger
├── swagger
├── security
├── common
└── currency
```

### Package Responsibilities

| Package | Responsibility |
|---|---|
| `account` | Wallet accounts, account entities, repositories, and account-related business logic |
| `transaction` | Deposits, withdrawals, transfers, transaction entities, and transaction history |
| `ledger` | Ledger entries and financial operation records |
| `swagger` | Swagger/OpenAPI configuration and API documentation |
| `security` | Spring Security configuration, JWT authentication, and authorization |
| `common` | Shared exceptions, exception handling, and common application components |
| `currency` | Currency validation and supported-currency policies |

## Financial Consistency

Financial operations are executed within database transactions to help ensure that related changes are committed or rolled back together.

The application uses account locking where needed to reduce the risk of concurrent operations causing inconsistent balances.

Ledger entries provide a record of financial movements. Account balances and transaction records should remain consistent with the corresponding ledger operations.

## Idempotency

Financial endpoints support idempotency keys to help prevent duplicate processing when a client retries a request.

- Repeating a request with the same idempotency key and the same request data returns the existing transaction result.
- Reusing the same key with different request data returns a conflict response (`409 Conflict`).

Clients should provide a unique idempotency key for each new financial operation and reuse that key when retrying the same operation.

## Authentication and Authorization

The API uses Spring Security and JWT-based authentication.

1. Register a user.
2. Log in to obtain a JWT.
3. Include the token in the `Authorization` header for protected endpoints.

```http
Authorization: Bearer <YOUR_JWT_TOKEN>
```

The application validates account ownership to prevent users from accessing or modifying other users' accounts and transactions.

## Getting Started

### Prerequisites

- Java 21 or a compatible configured JDK
- Maven
- Git

### Build the Project

```bash
mvn clean package
```

### Run the Application

```bash
mvn spring-boot:run
```

The application runs at:

```text
http://localhost:8081
```

Review `src/main/resources/application.yml` for application settings, database configuration, and the server port.

## API Endpoints

The following endpoints are available in the application.

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a user |
| `POST` | `/api/auth/login` | Authenticate and obtain a JWT |

### Accounts

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/accounts` | Create a wallet account |
| `GET` | `/api/accounts/{accountId}` | Retrieve an account and its balance |

### Transactions

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/transactions/deposit` | Deposit funds |
| `POST` | `/api/transactions/withdraw` | Withdraw funds |
| `POST` | `/api/transactions/transfer` | Transfer funds between accounts |
| `GET` | `/api/transactions/{transactionId}` | Retrieve a transaction |
| `GET` | `/api/transactions` | Retrieve the authenticated user's transaction history |

Financial endpoints require authentication and may require an idempotency key, depending on the operation.

## API Documentation

After starting the application, open Swagger UI:

```text
http://localhost:8081/swagger-ui/index.html
```

Use the **Authorize** option to provide a valid JWT and test protected endpoints.

## Monitoring and Health Checks

Spring Boot Actuator exposes health information and application metrics.

| Endpoint | Description |
|---|---|
| `/actuator/health` | Application health status |
| `/actuator/health/liveness` | Liveness probe |
| `/actuator/health/readiness` | Readiness probe |
| `/actuator/prometheus` | Prometheus-formatted metrics |

Prometheus metrics include HTTP request metrics and JVM metrics. Endpoint availability depends on the application's configuration and security rules.

## Error Handling

The application uses centralized exception handling to return appropriate HTTP status codes for common failures, including:

- `400 Bad Request` — invalid input or unsupported currency
- `401 Unauthorized` — missing or invalid authentication
- `404 Not Found` — account or transaction not found or not accessible
- `409 Conflict` — idempotency key reused with different request data
- `422 Unprocessable Entity` — insufficient balance

The exact response depends on the endpoint and the error condition.

## Currency Support

The application currently supports **IRR**. Unsupported currency codes are rejected by the currency validation logic.

## Development Notes

This project is intended as a practical backend engineering project demonstrating financial API design, persistence, transaction management, security, idempotency, and observability.

Potential future improvements include:

- Automated integration and concurrency tests
- Database migration management
- Production database configuration
- Docker and Docker Compose support
- CI/CD pipeline
- Expanded currency support
- Additional operational metrics and dashboards
