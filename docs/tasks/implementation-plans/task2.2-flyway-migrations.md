# Task 2.2: Flyway Migrations

## Overview
This task involves creating Flyway migration scripts to set up the database schema for the Sheets application. We'll create tables for users, sheets, and access mappings.

## Implementation Steps

### 1. Create Migration Directory Structure
Ensure the following directory structure exists:
```
src/main/resources/db/migration/
```

### 2. Create V1__create_users_table.sql
Create `src/main/resources/db/migration/V1__create_users_table.sql`:

```sql
-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on username for faster lookups
CREATE INDEX idx_users_username ON users(username);
```

### 3. Create V2__create_sheets_table.sql
Create `src/main/resources/db/migration/V2__create_sheets_table.sql`:

```sql
-- Sheets table
CREATE TABLE sheets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    max_length INTEGER NOT NULL DEFAULT 100,
    max_breadth INTEGER NOT NULL DEFAULT 100,
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on user_id for faster lookups
CREATE INDEX idx_sheets_user_id ON sheets(user_id);
```

### 4. Create V3__create_access_mappings_table.sql
Create `src/main/resources/db/migration/V3__create_access_mappings_table.sql`:

```sql
-- Access mapping table
CREATE TABLE access_mappings (
    id BIGSERIAL PRIMARY KEY,
    sheet_id BIGINT NOT NULL REFERENCES sheets(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    access_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(sheet_id, user_id)
);

-- Create indexes for faster lookups
CREATE INDEX idx_access_mappings_sheet_id ON access_mappings(sheet_id);
CREATE INDEX idx_access_mappings_user_id ON access_mappings(user_id);
```

### 5. Update SheetsApplication.kt to Enable Flyway
Update `src/main/kotlin/com/sheets/SheetsApplication.kt` to enable Flyway:

```kotlin
package com.sheets

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SheetsApplication

fun main(args: Array<String>) {
    runApplication<SheetsApplication>(*args)
}
```

### 6. Configure Flyway in application.yml
Update or create `src/main/resources/application.yml` to include Flyway configuration:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sheets
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    schemas: public
    clean-disabled: false
```

### 7. Run Flyway Migration
Execute the Flyway migration task to apply the migrations to the database:

```bash
./gradlew migrateTables
```

## Testing
1. Verify that the migration scripts run successfully
2. Check the database to ensure tables are created with the correct schema
3. Verify that indexes are created
4. Test foreign key constraints by attempting to insert invalid data

## Completion Criteria
- All migration scripts are created and properly versioned
- Tables are created in the database with the correct schema
- Foreign key constraints are properly defined
- Indexes are created for performance optimization
- Flyway is properly configured and enabled in the application
