# Task 2: Sheet Management Setup

## Overview
This task involves setting up the core components for Sheet Management, including data models, repositories, services, and controllers. It also includes setting up Flyway for database migrations and JOOQ for type-safe SQL queries.

## Implementation Steps

### 1. Create Data Models for Sheets and Access Control

#### Domain Models
Create the following domain models in `src/main/kotlin/com/sheets/models/domain`:

1. Sheet.kt
2. AccessMapping.kt
3. AccessType.kt (enum)

### 2. Setup Flyway Migrations
Create migration scripts in `src/main/resources/db/migration`:

1. V1__create_users_table.sql
2. V2__create_sheets_table.sql
3. V3__create_access_mappings_table.sql

### 3. Setup JOOQ Code Generation
Configure JOOQ in build.gradle.kts (already done) and ensure it generates code from the database schema.

### 4. Create Repositories
Create the following repositories in `src/main/kotlin/com/sheets/repositories`:

1. SheetRepository.kt (interface and implementation)
2. AccessRepository.kt (interface and implementation)

### 5. Create Services
Create the following services in `src/main/kotlin/com/sheets/services`:

1. SheetService.kt (interface and implementation)

### 6. Update OpenAPI Specification
Update `src/main/resources/openapi/api.yaml` to include Sheet Management endpoints:

1. POST /sheet/ (Create Sheet)
2. GET /sheet/ (List Sheets)
3. GET /sheet/{sheetId} (Get Sheet Details)
4. POST /sheet/share/{sheetId} (Share Sheet)

### 7. Generate OpenAPI Code
Run the OpenAPI code generation task to generate interfaces and models.

### 8. Create Controllers
Create the following controllers in `src/main/kotlin/com/sheets/controllers`:

1. SheetController.kt (implementing the generated interface)

### 9. Create Mappers
Create the following mappers in `src/main/kotlin/com/sheets/mappers`:

1. SheetMapper.kt (for mapping between domain models and DTOs)

## Detailed Implementation Plans
Each of these steps has a detailed implementation plan in a separate file:

1. [Sheet Domain Models](./task2.1-sheet-domain-models.md)
2. [Flyway Migrations](./task2.2-flyway-migrations.md)
3. [Sheet Repositories](./task2.3-sheet-repositories.md)
4. [Sheet Services](./task2.4-sheet-services.md)
5. [OpenAPI Specification](./task2.5-openapi-specification.md)
6. [Sheet Controller](./task2.6-sheet-controller.md)
7. [Sheet Mappers](./task2.7-sheet-mappers.md)

## Testing
1. Unit tests for services these should be according to the use cases
2. Unit test should also contain concurrency idempotency tests

## Completion Criteria
- All domain models are created
- Database migrations are set up and working
- JOOQ code is generated successfully
- Repositories are implemented and tested
- Services are implemented and tested
- OpenAPI specification is updated
- Controllers are implemented and tested
- Mappers are implemented
- All tests pass
