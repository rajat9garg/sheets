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
- **Database:** PostgreSQL 15.13
- **Programming Languages:** Kotlin 1.9.25, Java 17
- **Build Tool:** Gradle 8.13

### Database & ORM
- **Database System:** PostgreSQL 15.13 (downgraded from 16.9 for compatibility)
- **ORM Framework:** JOOQ 3.19.3
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
- `com.fasterxml.jackson.module:jackson-module-kotlin` - Kotlin support for Jackson
- `org.slf4j:slf4j-api` - Logging facade
- `ch.qos.logback:logback-classic` - Logging implementation

## Database Schema
### Tables
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
- Gradle 8.13

### Setup Instructions
1. Clone the repository
2. Start PostgreSQL database:
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
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=sheets
DB_USER=postgres
DB_PASSWORD=postgres

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
5. Merge after approval

## Testing Strategy
### Unit Tests
- JUnit 5 for unit testing
- MockK for mocking in Kotlin tests
- Repository and service layer unit tests

### Integration Tests
- Spring Boot Test for integration testing
- Test containers for database tests
- API endpoint testing

## Monitoring & Logging
### Application Logs
- SLF4J with Logback for logging
- Log levels: ERROR, WARN, INFO, DEBUG
- Detailed logging in repository and service layers

### Health Checks
- Health check endpoint at `/api/v1/health`
- Basic application status monitoring

## Security Considerations
### Authentication
- No authentication implemented (development environment)
- User ID provided in headers without verification

### Data Protection
- No sensitive data handling implemented yet

### Dependencies
- Regular dependency updates via Gradle
- Vulnerability scanning not yet implemented
