# OrderFlow

Event-driven order processing system built with Java 21 and Spring Boot.

## Project Structure

- `order-service` — order management, port 8081
- `inventory-service` — inventory management, port 8082

Both applications are managed by a single multi-module Maven project.

## Tech Stack

- Java 21
- Spring Boot 3.5
- Spring MVC
- Spring Boot Actuator
- Maven

## Running Locally

Open the root `pom.xml` as a Maven project in IntelliJ IDEA.

Run the following applications:

1. `OrderServiceApplication`
2. `InventoryServiceApplication`

Health endpoints:

- http://localhost:8081/actuator/health
- http://localhost:8082/actuator/health

## Build

From the root project directory:

```bash
mvn clean verify
```
