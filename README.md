
# OrderFlow

An event-driven order processing system built with Java, Spring Boot,
Apache Kafka, and PostgreSQL.

OrderFlow demonstrates how two independent microservices communicate
asynchronously, reserve inventory, and handle message delivery failures
without double-reserving stock.

## Tech Stack

- Java 21
- Spring Boot 3.5
- Spring Web and Spring Data JPA
- Spring Kafka and Apache Kafka
- PostgreSQL 16
- Flyway
- Docker Compose
- Maven (multi-module project)
- JUnit 5, Mockito, MockMvc
- Testcontainers

## Architecture

OrderFlow consists of two independent Spring Boot applications:

**Order Service** (port 8081)
- Creates and retrieves orders
- Stores orders in its own PostgreSQL database
- Writes events to a transactional outbox
- Publishes `OrderCreated` events to Kafka
- Processes inventory results and updates order statuses

**Inventory Service** (port 8082)
- Manages products and stock levels
- Consumes `OrderCreated` events
- Reserves inventory transactionally
- Prevents duplicate reservations using the order ID
- Publishes `InventoryResult` events to Kafka

Each service owns its database. There are no cross-service database
joins or JPA relationships.

## Order Lifecycle

1. A client sends `POST /api/orders`.
2. Order Service creates an order with status `PENDING`.
3. The order and its `OrderCreated` outbox event are saved in one
   PostgreSQL transaction.
4. The outbox publisher sends the event to Kafka.
5. Inventory Service consumes the event and attempts to reserve stock.
6. Inventory Service publishes an inventory result.
7. Order Service updates the order status to `CONFIRMED` or `REJECTED`.

Possible order statuses:

| Status | Meaning |
|---|---|
| `PENDING` | Waiting for inventory processing |
| `CONFIRMED` | Inventory was reserved |
| `REJECTED` | Inventory could not be reserved |

Order processing is asynchronous. The initial HTTP response normally
contains `PENDING`; the final status becomes available later.

## Kafka Events

### Topic: `order-created`

Published by Order Service and consumed by Inventory Service.

Example:

```json
{
  "orderId": 15,
  "items": [
    {
      "productId": 101,
      "quantity": 2
    },
    {
      "productId": 205,
      "quantity": 1
    }
  ]
}
```

The Kafka message key is the order ID.

### Topic: `inventory-result`

Published by Inventory Service and consumed by Order Service.

Successful reservation:

```json
{
  "orderId": 15,
  "status": "RESERVED",
  "reason": null
}
```

Rejected reservation:

```json
{
  "orderId": 16,
  "status": "REJECTED",
  "reason": "Insufficient stock for product: 101"
}
```

### Dead Letter Topics

- `order-created.DLT`
- `inventory-result.DLT`

Failed Kafka records are retried before being sent to a dead letter
topic, except for configured non-retryable errors.

A business rejection caused by insufficient stock is not sent to a
DLT. It produces a normal `REJECTED` inventory result.

## Reliability

### Transactional Outbox

Order Service saves the order and the corresponding event in the
same database transaction.

An outbox publisher periodically:

1. Selects unpublished events.
2. Locks selected rows using `FOR UPDATE SKIP LOCKED`.
3. Publishes the events to Kafka.
4. Marks them as published after Kafka acknowledges them.

The current configuration polls approximately every second and
processes up to 20 events per batch.

If publishing fails, the database transaction rolls back and
the event remains available for a later attempt.

### Idempotent Inventory Reservations

Inventory Service stores the outcome of each order in the
`inventory_reservations` table.

The order ID is the primary key.

A duplicate `OrderCreated` event returns the previously stored
result without reserving the same stock again.

PostgreSQL `INSERT ... ON CONFLICT DO NOTHING` handles concurrent
attempts to process the same order.

### Concurrent Stock Updates

Inventory reservations execute inside PostgreSQL transactions.

Product rows are locked using `PESSIMISTIC_WRITE` and acquired in
a consistent product ID order.

If any product has insufficient stock, the reservation is rejected.
Partial reservations are not committed.

### Delivery Guarantees

OrderFlow uses **at-least-once event processing**, not exactly-once
delivery.

Kafka messages may be delivered more than once. Idempotent inventory
processing and protected order status transitions make duplicate
deliveries safe for the implemented workflow.

Limitations:

- Orders may remain `PENDING` if a message is moved to a DLT.
- DLT messages require manual investigation or replay.
- The direct inventory reservation REST endpoint is intended for
  testing and does not provide order-ID-based idempotency.
- Event contracts are represented independently in each service.
- The Docker Compose environment is designed for local development.

## Getting Started

### Prerequisites

- JDK 21 or newer
- Docker Desktop
- IntelliJ IDEA or Maven

The Maven project targets Java 21.

### 1. Start Infrastructure

From the project root:

```bash
docker compose up -d
```

Docker Compose starts:

| Component | Host Port |
|---|---|
| Orders PostgreSQL | 5433 |
| Inventory PostgreSQL | 5434 |
| Apache Kafka | 9092 |

Verify:

```bash
docker compose ps
```

Flyway migrations run automatically when the Spring Boot applications
start.

### 2. Start Applications

Open the root Maven project in IntelliJ IDEA.

Run:

- `OrderServiceApplication`
- `InventoryServiceApplication`

Alternatively, with Maven installed, start each application in
a separate terminal from the project root:

```bash
mvn -pl order-service spring-boot:run
```

```bash
mvn -pl inventory-service spring-boot:run
```

### 3. Check Health Endpoints

Order Service:

http://localhost:8081/actuator/health

Inventory Service:

http://localhost:8082/actuator/health

Both should return:

```json
{
  "status": "UP"
}
```

## REST API

### Order Service

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/orders` | Create an order |
| `GET` | `/api/orders` | List orders |
| `GET` | `/api/orders/{id}` | Get order by ID |

Create order:

```http
POST http://localhost:8081/api/orders
Content-Type: application/json
```

```json
{
  "customerEmail": "customer@example.com",
  "items": [
    {
      "productId": 101,
      "quantity": 2
    },
    {
      "productId": 205,
      "quantity": 1
    }
  ]
}
```

Returns `201 Created` with an order initially in `PENDING` state.

Retrieve its current status:

```http
GET http://localhost:8081/api/orders/1
```

The result becomes `CONFIRMED` or `REJECTED` after Kafka processing.

### Inventory Service

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/products` | List products |
| `GET` | `/api/products/{id}` | Get product by ID |
| `POST` | `/api/inventory/reservations` | Manually reserve inventory |

The inventory database is initially populated with sample products:

| ID | SKU | Product | Initial Stock |
|---|---|---|---|
| 101 | `LAPTOP-001` | Laptop | 10 |
| 205 | `MOUSE-001` | Wireless Mouse | 50 |
| 310 | `KEYBOARD-001` | Mechanical Keyboard | 25 |
| 420 | `MONITOR-001` | 27-inch Monitor | 15 |

Available quantities change as orders are processed.

Example:

```http
GET http://localhost:8082/api/products/101
```

Manual inventory reservation:

```http
POST http://localhost:8082/api/inventory/reservations
Content-Type: application/json
```

```json
{
  "items": [
    {
      "productId": 101,
      "quantity": 2
    }
  ]
}
```

This endpoint directly changes stock. For the full event-driven
workflow, create an order through Order Service instead.

## Try the Full Workflow

Run this in PowerShell after starting both applications:

```powershell
$body = @{
    customerEmail = "demo@example.com"
    items = @(
        @{
            productId = 101
            quantity = 1
        }
    )
} | ConvertTo-Json -Depth 5

$order = Invoke-RestMethod `
    -Uri "http://localhost:8081/api/orders" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

$order | ConvertTo-Json -Depth 5

Start-Sleep -Seconds 3

Invoke-RestMethod `
    -Uri "http://localhost:8081/api/orders/$($order.id)" |
    ConvertTo-Json -Depth 5
```

The order should eventually become `CONFIRMED` if stock is available.

To test rejection, request a quantity larger than the available
stock and check for `REJECTED`.

Because processing is asynchronous, repeat the GET request if the
status is still `PENDING`.

## Testing

Tests cover:

- Order creation and input validation
- REST endpoints with MockMvc
- Order status transitions
- Inventory reservation logic
- Insufficient stock and transaction rollback
- Concurrent inventory reservations
- Duplicate event processing
- Transactional Outbox persistence and rollback
- Retrying outbox publication after a simulated Kafka failure

The project uses JUnit 5, Mockito, and Testcontainers.

Run all tests from the root:

```bash
mvn test
```

Or use **Maven > Lifecycle > test** in IntelliJ IDEA.

Docker Desktop must be running for Testcontainers-based tests.

The integration tests use real PostgreSQL containers. Kafka
publication failures in the Outbox test are simulated using Mockito.

## Project Structure

```text
OrderFlow/
├── compose.yaml
├── pom.xml
├── order-service/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/dev/portfolio/orderflow/order/
│       │   │   ├── domain/
│       │   │   ├── dto/
│       │   │   ├── messaging/
│       │   │   ├── outbox/
│       │   │   └── repository/
│       │   └── resources/db/migration/
│       └── test/
└── inventory-service/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/dev/portfolio/orderflow/inventory/
        │   │   ├── domain/
        │   │   ├── dto/
        │   │   ├── messaging/
        │   │   └── repository/
        │   └── resources/db/migration/
        └── test/
```

## Stop Infrastructure

```bash
docker compose down
```

Docker volumes retain PostgreSQL data between runs.

## Purpose

OrderFlow is a portfolio project focused on backend and distributed
systems concepts: asynchronous communication, transactional
consistency, database locking, idempotency, retry handling,
dead letter topics, and reliable event publication.
