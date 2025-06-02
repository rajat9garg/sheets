# Project Brief

**Created:** 2025-05-24  
**Status:** [ACTIVE]  
**Author:** [Your Name]  
**Last Modified:** 2025-06-05
**Last Updated By:** Cascade AI Assistant

## Table of Contents
- [Overview](#overview)
- [Objectives](#objectives)
- [Scope](#scope)
- [Technical Stack](#technical-stack)
- [Project Structure](#project-structure)
- [Setup Instructions](#setup-instructions)
- [Timeline & Phases](#timeline--phases)
- [Dependencies](#dependencies)
- [Assumptions & Constraints](#assumptions--constraints)

## Overview
Sheets is a collaborative spreadsheet application that allows users to create, edit, and share spreadsheets with formula support. The application features real-time updates, cell dependency tracking, and efficient formula evaluation to provide a responsive user experience.

## Objectives
- Build a scalable and responsive spreadsheet application
- Implement robust cell dependency management for formula evaluation
- Support collaborative editing with proper access control
- Ensure high performance through caching and asynchronous processing
- Create a maintainable and extensible codebase using modern architecture patterns

## Scope
### In Scope
- User management and authentication
- Spreadsheet creation, editing, and sharing
- Cell dependency tracking and formula evaluation
- Access control with different permission levels
- Real-time updates for collaborative editing
- Performance optimization through caching and async processing

### Out of Scope
- Advanced spreadsheet features (charts, pivot tables, etc.)
- Mobile application
- Offline editing
- Integration with external data sources
- Advanced collaboration features (comments, suggestions, etc.)

## Technical Stack
- **Backend:** Spring Boot 3.5.0 with Kotlin 1.9.25 (Java 17)
- **Database:** 
  - PostgreSQL 15.13 for relational data (users, sheets, access mappings)
  - MongoDB 6.0 for cell and dependency data
  - Redis 7.0 for caching cell dependencies
- **ORM:** JOOQ 3.19.3 for type-safe SQL queries
- **Migration:** Flyway 9.16.1 for database schema migrations
- **Build Tool:** Gradle 8.13
- **Testing:** JUnit 5, MockK, TestContainers
- **API Documentation:** OpenAPI 3.0.3

## Project Structure
```
sheets/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/
│   │   │       └── sheets/
│   │   │           ├── api/           # API controllers
│   │   │           ├── config/        # Application configuration
│   │   │           ├── domain/        # Domain models
│   │   │           ├── repositories/  # Data access layer
│   │   │           ├── services/      # Business logic
│   │   │           └── utils/         # Utility classes
│   │   └── resources/
│   │       ├── db/
│   │       │   └── migration/        # Flyway migration scripts
│   │       ├── openapi/              # OpenAPI specifications
│   │       └── application.yml       # Application configuration
│   └── test/
│       └── kotlin/
│           └── com/
│               └── sheets/           # Test classes
├── build.gradle.kts                  # Gradle build configuration
├── docker-compose.yml                # Docker configuration
└── memory-bank/                      # Project documentation
```

## Setup Instructions
### Prerequisites
- JDK 17
- Docker and Docker Compose
- PostgreSQL 15.13
- MongoDB 6.0
- Redis 7.0
- Gradle 8.13

### Development Setup
1. Clone the repository
2. Start the required services:
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
5. Access the API at `http://localhost:8080/api/v1`

## Timeline & Phases
### Phase 1: Foundation (Completed)
- Setup project structure and dependencies
- Configure PostgreSQL, MongoDB, and Redis
- Implement basic user management
- Create database schema and migrations

### Phase 2: Core Features (In Progress)
- Implement cell dependency management system
- Develop formula evaluation engine
- Create sheet access control
- Build basic API endpoints

### Phase 3: Performance Optimization (Planned)
- Optimize cell dependency updates
- Refine Redis caching strategy
- Implement batch processing for formula evaluation
- Add performance monitoring

### Phase 4: API Completion and Testing (Planned)
- Complete all API endpoints
- Implement comprehensive testing
- Finalize documentation
- Prepare for deployment

## Dependencies
### External Dependencies
- Spring Boot Starters
  - spring-boot-starter-web
  - spring-boot-starter-data-mongodb
  - spring-boot-starter-data-redis
- Database Drivers
  - postgresql:42.6.0
- ORM and Migrations
  - jooq:3.19.3
  - flyway-core:9.16.1
- Redis Client
  - lettuce-core
- JSON Processing
  - jackson-module-kotlin

### Internal Dependencies
- Cell Service depends on Cell Dependency Service
- Formula Evaluation depends on Cell Service
- Access Control Service depends on User Service

## Assumptions & Constraints
### Assumptions
- Users have modern web browsers with JavaScript enabled
- Network connectivity is reliable for real-time updates
- Formula complexity is limited to standard spreadsheet functions
- Cell dependencies are typically not deeply nested

### Constraints
- Performance may degrade with very large spreadsheets (>10,000 cells)
- Complex circular dependency detection may impact performance
- Redis cache size must be monitored and managed
- Asynchronous updates may introduce slight delays in formula evaluation
