# Technical Context

**Created:** 2025-05-24  
**Status:** [ACTIVE]  
**Author:** [Your Name]  

## Table of Contents
- [Technology Stack](#technology-stack)
- [Database Schema](#database-schema)
- [Development Environment](#development-environment)
- [Build & Deployment](#build--deployment)
- [Infrastructure](#infrastructure)
- [Development Workflow](#development-workflow)
- [Testing Strategy](#testing-strategy)
- [Monitoring & Logging](#monitoring--logging)
- [Security Considerations](#security-considerations)
- [Expression Evaluation](#expression-evaluation)
- [Error Handling](#error-handling)

## Technology Stack
### Core Technologies
- **Backend Framework:** Spring Boot 3.5.0
- **Database:** PostgreSQL 15.13, MongoDB 6.0, Redis 7.0
- **Programming Languages:** Kotlin 1.9.25, Java 17
- **Build Tool:** Gradle 8.13

### Important Notes
- **Alphabetical Column Notation:** System uses A1 style notation (e.g., A1, B2, C3) for cell references
- **Cell ID Format:** Cell IDs follow the format `sheetId:row:column` where column is an alphabetical letter
- **Expression Evaluation:** Supports arithmetic operations, function calls, cell references, and ranges
- **PostgreSQL Enum Types:** Custom enum type `access_type` with values `READ`, `WRITE`, `ADMIN`, `OWNER`
- **Foreign Key Constraints:** Removed foreign key constraints on `sheets.user_id` and `access_mappings.user_id` to enable development flexibility
- **ID Type Handling:** Domain models use UUID for IDs while some database tables use BIGINT
- **Health Check Endpoint:** Implemented at `/api/v1/health` with basic status monitoring
- **Redis Caching:** Cell dependencies cached with 24-hour TTL
- **Asynchronous Processing:** Using Spring's @Async for dependent cell updates
- **Custom Exception Hierarchy:** Implemented a hierarchy of custom exceptions (e.g., `SheetLockException`, `CircularReferenceException`) for specific error scenarios.
- **Global Exception Handling:** Centralized error handling via `GlobalExceptionHandler` to provide standardized `ErrorResponse` objects with appropriate HTTP status codes and detailed error information.

### JOOQ Configuration
- **Version:** 3.19.3 (OSS Edition)
- **Generated Code Location:** `build/generated/jooq`
- **Target Package:** `com.sheets.infrastructure.jooq`
- **Key Features:**
  - Record Generation
  - Immutable POJOs
  - Fluent Setters
  - Kotlin Data Classes
  - Java Time Types

### Dependencies
#### Runtime Dependencies
- `org.springframework.boot:spring-boot-starter-jooq` - JOOQ integration for Spring Boot
- `org.jooq:jooq:3.19.3` - JOOQ core library
- `org.postgresql:postgresql:42.6.0` - PostgreSQL JDBC driver
- `org.flywaydb:flyway-core` - Database migration tool
- `org.springframework.boot:spring-boot-starter-data-mongodb` - MongoDB integration
- `org.springframework.boot:spring-boot-starter-data-redis` - Redis integration
- `io.lettuce:lettuce-core` - Redis client
- `com.fasterxml.jackson.module:jackson-module-kotlin` - Kotlin support for Jackson
- `org.slf4j:slf4j-api` - Logging facade
- `ch.qos.logback:logback-classic` - Logging implementation

## Database Schema
### PostgreSQL Tables
#### users
- **Primary Key:** `id` (BIGINT)
- **Columns:**
  - `id` - BIGINT, Primary Key
  - `name` - VARCHAR(255)
  - `email` - VARCHAR(255)
  - `created_at` - TIMESTAMP
  - `updated_at` - TIMESTAMP
- **Indexes:** 
  - Primary key on `id`
  - Unique index on `email`
- **Triggers:** 
  - `set_updated_at` - Updates `updated_at` on record update

#### sheets
- **Primary Key:** `id` (BIGINT)
- **Columns:**
  - `id` - BIGINT, Primary Key
  - `user_id` - BIGINT
  - `name` - VARCHAR(255)
  - `data` - JSONB
  - `created_at` - TIMESTAMP
  - `updated_at` - TIMESTAMP
- **Indexes:** 
  - Primary key on `id`
  - Index on `user_id`
- **Foreign Keys:** 
  - None (removed constraint on `user_id` for development flexibility)
- **Triggers:** 
  - `set_updated_at` - Updates `updated_at` on record update

#### access_mappings
- **Primary Key:** `id` (BIGINT)
- **Columns:**
  - `id` - BIGINT, Primary Key
  - `sheet_id` - BIGINT
  - `user_id` - BIGINT
  - `access_type` - ENUM ('READ', 'WRITE', 'ADMIN', 'OWNER')
  - `created_at` - TIMESTAMP
  - `updated_at` - TIMESTAMP
- **Indexes:** 
  - Primary key on `id`
  - Unique index on (`sheet_id`, `user_id`)
  - Index on `user_id`
- **Foreign Keys:** 
  - None (removed constraint on `user_id` for development flexibility)
- **Triggers:** 
  - `set_updated_at` - Updates `updated_at` on record update

### MongoDB Collections
#### cells
- **Primary Key:** `id` (String, format: "sheetId:row:column")
- **Fields:**
  - `id` - String, Primary Key
  - `sheetId` - Long
  - `row` - Integer
  - `column` - String (alphabetical column identifier, e.g., "A", "B", "C")
  - `data` - String (raw formula or value)
  - `evaluatedValue` - String (calculated result)
  - `dataType` - Enum ('STRING', 'NUMBER', 'BOOLEAN', 'ERROR', 'FORMULA')
  - `created_at` - Date
  - `updated_at` - Date
- **Indexes:**
  - Primary key on `id`
  - Index on `sheetId`

#### cell_dependencies
- **Primary Key:** `id` (String)
- **Fields:**
  - `id` - String, Primary Key
  - `sourceCellId` - String (cell that depends on another)
  - `targetCellId` - String (cell being depended on)
  - `sheetId` - Long
  - `created_at` - Date
- **Indexes:**
  - Primary key on `id`
  - Index on `sourceCellId`
  - Index on `targetCellId`
  - Index on `sheetId`
  - Compound index on (`sourceCellId`, `targetCellId`)

### Redis Cache
#### Key Patterns
- **Dependency Key:** `dependency:{sourceCellId}:{targetCellId}`
- **Source Dependencies Set:** `source:dependencies:{sourceCellId}`
- **Target Dependencies Set:** `target:dependencies:{targetCellId}`
- **Sheet Dependencies Set:** `sheet:dependencies:{sheetId}`

#### TTL Settings
- **Default TTL:** 24 hours for all cache entries
- **Key Types:**
  - String values for individual dependencies
  - Set values for dependency collections

### Custom Types
#### access_type
- **Type:** ENUM
- **Values:** 'READ', 'WRITE', 'ADMIN', 'OWNER'
- **Usage:** Used in `access_mappings.access_type` column
- **Notes:** Recreated in V5 migration to include 'OWNER' value

### Migrations
1. **V1__create_users_table.sql** - Creates the users table
2. **V2__create_sheets_table.sql** - Creates the sheets table with foreign key to users
3. **V3__create_access_mappings_table.sql** - Creates access_mappings table and access_type enum
4. **V4__remove_sheets_user_id_foreign_key.sql** - Removes foreign key constraint on sheets.user_id
5. **V5__add_owner_to_access_type_enum.sql** - Recreates access_type enum to include OWNER value
6. **V6__remove_access_mappings_user_id_foreign_key.sql** - Removes foreign key constraint on access_mappings.user_id

## Development Environment
### Prerequisites
- JDK 17
- Docker and Docker Compose
- PostgreSQL 15.13
- MongoDB 6.0
- Redis 7.0
- Gradle 8.13

### Setup Instructions
1. Clone the repository
2. Start database services:
   ```bash
   docker-compose up -d
   ```
3. Build the application:
   ```bash
   ./gradlew build
   ```
4. Run the application:
   ```bash
   ./gradlew bootRun
   ```

### Configuration
#### Environment Variables
```
# PostgreSQL Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=sheets
DB_USER=postgres
DB_PASSWORD=postgres

# MongoDB
MONGO_URI=mongodb://mongo:mongopass@localhost:27017/sheets?authSource=admin
MONGO_DATABASE=sheets

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redispass
REDIS_DATABASE=0

# Application
SERVER_PORT=8080
```

## Build & Deployment
### Build Process
```bash
./gradlew clean build
```

### Deployment
#### Development
```bash
./gradlew bootRun
```

#### Production
```bash
java -jar build/libs/sheets-0.0.1-SNAPSHOT.jar
```

## Infrastructure
### Development
- Local PostgreSQL 15.13 database via Docker
- Local MongoDB 6.0 database via Docker
- Local Redis 7.0 cache via Docker
- Spring Boot embedded Tomcat server

### Staging/Production
- Not yet configured

## Development Workflow
### Branching Strategy
- Feature branches from `main`
- Pull requests for code review
- Merge to `main` after approval

### Code Review Process
1. Create feature branch from `main`
2. Make changes and commit with descriptive messages
3. Push and create pull request
4. Address review comments
5. Merge to `main` after approval

## Testing Strategy
### Unit Testing
- JUnit 5 for unit tests
- MockK for mocking in Kotlin
- Test coverage target: 80%

### Integration Testing
- Spring Boot Test for integration tests
- TestContainers for database tests
- API tests with RestAssured

### Performance Testing
- JMeter for load testing
- Focus on cell dependency update performance
- Redis cache hit rate monitoring

## Monitoring & Logging
### Logging
- SLF4J with Logback
- Log levels:
  - INFO for production
  - DEBUG for development
- Structured logging format:
  ```
  %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
  ```

### Metrics
- Spring Boot Actuator for basic metrics
- Health check endpoint at `/api/v1/health`
- Custom metrics for:
  - Cell update response time
  - Redis cache hit rate
  - Circular dependency detection time

## Security Considerations
### Authentication & Authorization
- Not yet implemented

### Data Protection
- No sensitive data stored
- Redis password protection
- MongoDB authentication

### API Security
- Basic input validation
- No rate limiting yet

## Expression Evaluation
### Expression Functions
- **SUM:** Calculates the sum of values in cell references and ranges
- **AVERAGE:** Calculates the average of values in cell references and ranges
- **MIN:** Finds the minimum value among cell references and ranges
- **MAX:** Finds the maximum value among cell references and ranges

### Cell Reference Formats
- **A1 Notation:** Uses alphabetical column and numeric row (e.g., A1, B2, C3)
- **Legacy Numeric Notation:** Uses row:column format (e.g., 1:1, 2:3)
- **Cell Ranges:** Supports both A1 notation (e.g., A1:C3) and legacy format (e.g., 1:1-3:3)

### Column Conversion Utilities
- **Column Letter to Number:** Converts alphabetical column to numeric index
- **Column Number to Letter:** Converts numeric index to alphabetical column

## Error Handling
### Custom Exceptions
- **Base Exception:** `SheetException` serves as the base for all custom exceptions.
- **Specific Exceptions:**
  - `ResourceLockException` (and its subclasses `SheetLockException`, `CellLockException`): For concurrency control and resource locking issues.
  - `CircularReferenceException`: For detecting and handling circular dependencies in cell formulas.
  - `CellDependencyException`: For issues related to cell dependencies preventing operations.
  - `PersistenceException`: For errors during data persistence (Redis, MongoDB).

### Global Exception Handler (`GlobalExceptionHandler`)
- **Purpose:** Provides a centralized mechanism to handle all exceptions thrown within the application.
- **Functionality:**
  - Catches custom and standard exceptions.
  - Maps exceptions to appropriate HTTP status codes (e.g., 409 Conflict, 400 Bad Request).
  - Formats error responses into a consistent `ErrorResponse` structure, including `status`, `error`, `message`, `timestamp`, and a `details` field for additional context.
  - Ensures user-friendly error messages are returned to the client.

### Error Response Structure
- **Model:** Defined in `api.yaml` as `ErrorResponse`
- **Fields:**
  - `status`: HTTP status code (e.g., 400, 404, 409, 500)
  - `error`: Error type (e.g., "Bad Request", "Not Found", "Conflict")
  - `message`: User-friendly error message
  - `path`: Request path that triggered the error
  - `timestamp`: Time when the error occurred
  - `details`: Additional error details specific to the error type (e.g., resource ID, lock owner, retry information)

This approach significantly improves error clarity, debugging, and client-side error handling capabilities.
