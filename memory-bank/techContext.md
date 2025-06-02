# Technical Context

**Created:** 2025-05-24  
**Status:** [ACTIVE]  
**Author:** [Your Name]  
**Last Modified:** 2025-06-02
**Last Updated By:** Cascade AI Assistant

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

## Technology Stack
### Core Technologies
- **Backend Framework:** Spring Boot 3.5.0
- **Database:** PostgreSQL 15.13, MongoDB 6.0, Redis 7.0
- **Programming Languages:** Kotlin 1.9.25, Java 17
- **Build Tool:** Gradle 8.13

### Database & ORM
- **Relational Database:** PostgreSQL 15.13 (downgraded from 16.9 for compatibility)
- **Document Database:** MongoDB 6.0 for cell and dependency storage
- **Cache:** Redis 7.0 for dependency caching
- **ORM Framework:** JOOQ 3.19.3 for PostgreSQL
- **MongoDB Client:** Spring Data MongoDB
- **Redis Client:** Spring Data Redis with Lettuce
- **Database Migration:** Flyway 9.16.1
  - Migration Location: `src/main/resources/db/migration`
  - Schema: `public`
  - Migration Table: `flyway_schema_history`
  - Clean Disabled: `true` (safety measure)
  - Baseline on Migrate: `true`

### Important Notes
- **PostgreSQL Enum Types:** Custom enum type `access_type` with values `READ`, `WRITE`, `ADMIN`, `OWNER`
- **Foreign Key Constraints:** Removed foreign key constraints on `sheets.user_id` and `access_mappings.user_id` to enable development flexibility
- **ID Type Handling:** Domain models use UUID for IDs while some database tables use BIGINT
- **Health Check Endpoint:** Implemented at `/api/v1/health` with basic status monitoring
- **Redis Caching:** Cell dependencies cached with 24-hour TTL
- **Asynchronous Processing:** Using Spring's @Async for dependent cell updates

#### JOOQ Configuration
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
- **Primary Key:** `id` (String, format: "sheetId:cellRef")
- **Fields:**
  - `id` - String, Primary Key
  - `sheetId` - Long
  - `cellRef` - String (e.g., "A1", "B2")
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
