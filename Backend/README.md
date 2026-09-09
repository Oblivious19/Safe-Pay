# SafePay API

Spring Boot REST API foundation for SafePay, a simulated banking transaction-control application.

## Prerequisites

- Java 17
- Maven 3.6.3 or later
- Oracle Database for the `oracle` profile

## Run locally without Oracle

The default `local` profile starts the API without a database so the health endpoint can be verified while the Phase 1 schema is being finalized.

```powershell
mvn spring-boot:run
```

Then request `GET http://localhost:8080/api/health`.

## Run with Oracle

Create a user-level environment configuration; do not commit credentials. Set the following variables and activate the `oracle` profile:

```text
SPRING_PROFILES_ACTIVE=oracle
SAFEPAY_DB_URL=jdbc:oracle:thin:@//host:1521/service
SAFEPAY_DB_USERNAME=safepay_user
SAFEPAY_DB_PASSWORD=replace-with-a-secret
```

The Oracle profile uses `ddl-auto=validate`: the application validates the approved Phase 1 schema and never generates, updates, or deletes database tables.

## Test

```powershell
mvn test
```

Business services, entities, and repositories will be added after the Oracle schema is approved in Phase 1.
