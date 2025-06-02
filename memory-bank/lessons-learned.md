# Lessons Learned

**Created:** 2025-05-24  
**Last Updated:** 2025-06-05  
**Last Updated By:** Cascade AI Assistant  
**Related Components:** Database, ORM, PostgreSQL, Flyway, Repository Implementation, MongoDB, Redis, Cell Dependencies

## Database & ORM Configuration

### Flyway Configuration
1. **Version Compatibility**
   - **Lesson:** Flyway 9.16.1 has compatibility issues with PostgreSQL 15.13
   - **Solution:** Temporarily disabled Flyway auto-configuration in `application.yml` and `@SpringBootApplication`
   - **Best Practice:** Always verify Flyway version compatibility with your PostgreSQL version before implementation

2. **Migration File Naming**
   - Always use the correct naming convention: `V{version}__{description}.sql`
   - Double underscores are required in the filename
   - Example: `V1__create_users_table.sql`

3. **Migration Location**
   - Default location is `src/main/resources/db/migration`
   - Can be customized in `build.gradle.kts` but requires explicit configuration

4. **Clean Operation**
   - Disable clean by default in production (`cleanDisabled = true`)
   - Always test migrations in a development environment first

### JOOQ Configuration
1. **Dependency Management**
   - Ensure the JOOQ version matches between the plugin and runtime dependencies
   - Add PostgreSQL JDBC driver to both runtime and JOOQ generator classpaths
   - **Lesson:** Use `implementation` for runtime dependencies and `jooqCodegen` for code generation dependencies
   - **Example:**
     ```kotlin
     dependencies {
         implementation("org.postgresql:postgresql:42.6.0")
         jooqCodegen("org.postgresql:postgresql:42.6.0")
     }
     ```

2. **Code Generation**
   - Run `./gradlew clean generateJooq` after schema changes
   - Generated code goes to `build/generated/jooq` by default
   - Configure the target package for generated code in `build.gradle.kts`

3. **Kotlin Support**
   - Enable Kotlin data classes with `isImmutablePojos = true`
   - Use `isFluentSetters = true` for better Kotlin integration

### Build Configuration
1. **Gradle Setup**
   - Use the correct plugin version (we used `nu.studer.jooq` version `7.1`)
   - Configure JOOQ tasks in the `jooq` block
   - Ensure proper task dependencies (e.g., `generateJooq` should run after `flywayMigrate`)
   - **Lesson:** When Flyway is disabled, ensure database schema is manually created before JOOQ code generation
   - **Example:**
     ```kotlin
     tasks.named<org.jooq.meta.jaxb.Generate>("generateJooq") {
         // Disable Flyway dependency when Flyway is disabled
         if (!project.hasProperty("disableFlyway") || project.property("disableFlyway") != "true") {
             dependsOn("flywayMigrate")
         } else {
             logger.lifecycle("Skipping Flyway migration as it's disabled")
         }
     }
     ```

2. **Error Handling**
   - Common error: `ClassNotFoundException` for JDBC driver - ensure it's in the correct configuration
   - Check Gradle logs with `--info` or `--debug` for detailed error information

## PostgreSQL Enum Type Handling

### Enum Type Alignment
1. **Domain Model and Database Enum Synchronization**
   - **Lesson:** PostgreSQL enum types must be kept in sync with Kotlin enum classes
   - **Problem:** The PostgreSQL enum `access_type` was missing the `OWNER` value present in the Kotlin `AccessType` enum
   - **Solution:** Created a migration to recreate the PostgreSQL enum type with all values including `OWNER`
   - **Best Practice:** Always ensure database enum types match exactly with application enum classes

2. **PostgreSQL Enum Type Constraints**
   - **Lesson:** PostgreSQL doesn't allow adding values to an enum type in a transaction
   - **Solution:** Created a migration that recreates the enum type with all values
   - **Best Practice:** When adding values to a PostgreSQL enum type, you must:
     1. Create a new enum type with all values
     2. Update the column type to use the new enum
     3. Drop the old enum type

3. **Enum Type Casting in SQL Queries**
   - **Lesson:** String values must be explicitly cast to enum types in SQL queries
   - **Problem:** Repository methods were passing string values directly to enum-typed columns
   - **Solution:** Updated SQL queries to use `?::access_type` syntax for proper casting
   - **Best Practice:** Always use explicit casting when passing enum values in SQL queries

## Database Foreign Key Constraints

1. **Foreign Key Constraints in Development**
   - **Lesson:** Foreign key constraints can block development when using test data
   - **Problem:** Foreign key constraints on `sheets.user_id` and `access_mappings.user_id` caused errors with non-existent user IDs
   - **Solution:** Created migrations to remove these constraints for development flexibility
   - **Best Practice:** Consider relaxing foreign key constraints during development and testing phases

2. **Migration Strategy for Constraint Removal**
   - **Lesson:** Removing constraints requires careful migration planning
   - **Solution:** Created separate migration files for each constraint removal
   - **Best Practice:** Document constraint removals clearly and consider reinstating them in production if data integrity is critical

## ID Type Handling

1. **Domain Model and Database ID Type Mismatch**
   - **Lesson:** Domain models may use different ID types than the database
   - **Problem:** Domain models used UUID for IDs while database used BIGINT for some tables
   - **Solution:** Implemented conversion logic in repository layer to handle UUID and BIGINT ID mappings
   - **Best Practice:** Document ID type mismatches clearly and implement consistent conversion in repository layer

2. **UUID Generation for Domain Objects**
   - **Lesson:** When using UUIDs in domain models with BIGINT in database, UUID generation must be handled carefully
   - **Solution:** Generate UUIDs for domain objects while correctly handling BIGINT IDs in database operations
   - **Best Practice:** Implement consistent ID generation and conversion strategies across all repositories

## Repository Implementation

1. **Error Handling in Repository Layer**
   - **Lesson:** Robust error handling is critical in repository implementations
   - **Solution:** Added detailed exception handling with specific error messages for different failure scenarios
   - **Best Practice:** Catch specific exceptions, log detailed error information, and rethrow with meaningful context

2. **Logging in Repository Methods**
   - **Lesson:** Detailed logging helps troubleshoot database interaction issues
   - **Solution:** Added logging for all repository method entry/exit points and error conditions
   - **Best Practice:** Log method parameters (excluding sensitive data), execution time, and results

3. **Timestamp Conversion**
   - **Lesson:** Timestamp handling requires explicit conversion between database and Kotlin types
   - **Solution:** Implemented proper conversion between database timestamps and Kotlin LocalDateTime
   - **Best Practice:** Use consistent timestamp conversion patterns across all repositories

## Spring Boot Integration

### Health Check Implementation
1. **Basic Health Check**
   - Implemented at `/api/v1/health`
   - Returns basic application status and timestamp
   - **Lesson:** Keep health checks lightweight and fast
   - **Improvement Needed:** Add database connectivity check

2. **Configuration Management**
   - Use `application.yml` for environment-specific configurations
   - **Lesson:** Externalize database configuration for different environments
   - **Example:**
     ```yaml
     spring:
       datasource:
         url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/sheets}
         username: ${DATABASE_USER:postgres}
         password: ${DATABASE_PASSWORD:postgres}
     ```

## Service Layer Adaptations

1. **Temporary Feature Disabling**
   - **Lesson:** Sometimes features need to be temporarily disabled to unblock development
   - **Problem:** Owner-related access mapping features caused errors due to enum type mismatch
   - **Solution:** Temporarily disabled these features in the service layer until enum issues were fixed
   - **Best Practice:** Clearly document disabled features, implement graceful fallbacks, and plan for re-enabling

2. **Graceful Degradation**
   - **Lesson:** Services should degrade gracefully when dependent features are unavailable
   - **Solution:** Temporarily replaced `OWNER` access type with `READ` for owned sheets
   - **Best Practice:** Implement fallback behavior that maintains core functionality when possible

## Cell Dependency Management

### Dual Storage Strategy
1. **MongoDB and Redis Integration**
   - **Lesson:** Using dual storage with MongoDB for persistence and Redis for caching provides optimal performance
   - **Solution:** Implemented CellDependencyService with MongoDB repository for persistence and Redis repository for caching
   - **Best Practice:** Follow the cache-aside pattern where Redis serves as cache and MongoDB as source of truth
   - **Example:**
     ```kotlin
     override fun getDependenciesBySourceCellId(sourceCellId: String): List<CellDependency> {
         // Try cache first
         val cachedDependencies = cellDependencyRedisRepository.getDependenciesBySourceCellId(sourceCellId)
         if (cachedDependencies.isNotEmpty()) {
             return cachedDependencies
         }
         
         // Fall back to MongoDB if cache miss
         val dependencies = cellDependencyRepository.findBySourceCellId(sourceCellId)
         
         // Populate cache for future requests
         if (dependencies.isNotEmpty()) {
             cellDependencyRedisRepository.saveDependencies(dependencies)
         }
         
         return dependencies
     }
     ```

2. **Cache Invalidation Strategy**
   - **Lesson:** Cache invalidation is critical for maintaining data consistency
   - **Problem:** Stale cache entries can lead to incorrect formula evaluation
   - **Solution:** Implemented TTL-based expiration (24 hours) and explicit invalidation on updates
   - **Best Practice:** Use a combination of TTL and explicit invalidation for optimal cache freshness

3. **Batch Operations**
   - **Lesson:** Batch operations significantly improve performance for bulk dependency updates
   - **Solution:** Implemented batch methods for creating and deleting dependencies
   - **Best Practice:** Use Redis pipelining and MongoDB bulk operations for efficient batch processing

### Redis Caching Implementation

1. **Key Design Patterns**
   - **Lesson:** Well-designed Redis keys are essential for efficient lookups
   - **Solution:** Implemented structured key patterns for different dependency relationships
   - **Best Practice:** Use consistent key naming conventions with colons as separators
   - **Example:**
     ```
     dependency:{sourceCellId}:{targetCellId} -> Individual dependency
     source:dependencies:{sourceCellId} -> Set of all dependencies for a source cell
     target:dependencies:{targetCellId} -> Set of all dependencies for a target cell
     sheet:dependencies:{sheetId} -> Set of all dependencies in a sheet
     ```

2. **TTL Configuration**
   - **Lesson:** TTL settings need to balance cache freshness with hit rate
   - **Problem:** Fixed TTL may not be optimal for all usage patterns
   - **Solution:** Implemented 24-hour TTL with plans to refine based on usage patterns
   - **Best Practice:** Monitor cache hit rates and adjust TTL accordingly

3. **Error Handling in Redis Operations**
   - **Lesson:** Redis operations can fail due to connectivity issues or memory constraints
   - **Solution:** Implemented robust error handling with fallback to MongoDB
   - **Best Practice:** Always catch Redis exceptions and gracefully degrade to primary data source
   - **Example:**
     ```kotlin
     try {
         return redisTemplate.opsForValue().get(key)
     } catch (e: Exception) {
         logger.error("Failed to retrieve from Redis: ${e.message}")
         return null
     }
     ```

4. **Connection Pool Management**
   - **Lesson:** Redis connection pool settings significantly impact performance
   - **Solution:** Configured connection pool with appropriate timeouts and max connections
   - **Best Practice:** Tune connection pool settings based on load testing results

### Circular Dependency Detection

1. **Depth-First Search Algorithm**
   - **Lesson:** DFS is efficient for detecting cycles in dependency graphs
   - **Solution:** Implemented DFS with visited and recursion stacks
   - **Best Practice:** Optimize for both correctness and performance
   - **Example:**
     ```kotlin
     private fun detectCircularDependency(
         sourceCellId: String,
         targetCellId: String,
         visited: MutableSet<String>,
         recursionStack: MutableSet<String>
     ): Boolean {
         if (recursionStack.contains(targetCellId)) {
             return true // Circular dependency detected
         }
         
         if (visited.contains(targetCellId)) {
             return false // Already checked, no circular dependency
         }
         
         visited.add(targetCellId)
         recursionStack.add(targetCellId)
         
         val dependencies = getDependenciesBySourceCellId(targetCellId)
         for (dependency in dependencies) {
             if (detectCircularDependency(sourceCellId, dependency.targetCellId, visited, recursionStack)) {
                 return true
             }
         }
         
         recursionStack.remove(targetCellId)
         return false
     }
     ```

2. **Performance Optimization**
   - **Lesson:** Circular dependency detection can be expensive for large dependency graphs
   - **Solution:** Implemented caching of dependency maps and early termination
   - **Best Practice:** Use memoization to avoid redundant calculations

3. **Error Reporting**
   - **Lesson:** Clear error messages are essential for debugging circular dependencies
   - **Solution:** Implemented detailed error messages with dependency path information
   - **Best Practice:** Include the complete dependency cycle in error messages

### Asynchronous Cell Updates

1. **Spring @Async Configuration**
   - **Lesson:** Proper thread pool configuration is critical for @Async performance
   - **Solution:** Configured dedicated thread pool with appropriate size and queue capacity
   - **Best Practice:** Size thread pool based on available CPU cores and expected workload
   - **Example:**
     ```kotlin
     @Configuration
     @EnableAsync
     class AsyncConfig {
         @Bean(name = ["taskExecutor"])
         fun taskExecutor(): Executor {
             val executor = ThreadPoolTaskExecutor()
             executor.corePoolSize = 5
             executor.maxPoolSize = 10
             executor.queueCapacity = 25
             executor.setThreadNamePrefix("CellUpdater-")
             executor.initialize()
             return executor
         }
     }
     ```

2. **Error Handling in Async Methods**
   - **Lesson:** Error handling in async methods requires special attention
   - **Problem:** Exceptions in @Async methods are lost if not properly handled
   - **Solution:** Implemented AsyncUncaughtExceptionHandler and detailed logging
   - **Best Practice:** Use CompletableFuture for better error handling in async operations

3. **Deadlock Prevention**
   - **Lesson:** Async operations on interdependent cells can cause deadlocks
   - **Solution:** Implemented ordered processing based on dependency graph topology
   - **Best Practice:** Process cells in topological order to prevent deadlocks

## MongoDB Integration

1. **Document Design for Cells**
   - **Lesson:** Document design significantly impacts query performance
   - **Solution:** Designed cell documents with composite IDs and appropriate indexing
   - **Best Practice:** Use compound indexes for frequently queried fields
   - **Example:**
     ```kotlin
     @Document(collection = "cells")
     data class CellDocument(
         @Id val id: String, // Format: "sheetId:cellRef"
         val sheetId: Long,
         val cellRef: String,
         val data: String,
         val evaluatedValue: String,
         val dataType: CellDataType,
         val createdAt: Date,
         val updatedAt: Date
     )
     
     // Indexes
     @Indexed(direction = IndexDirection.ASCENDING)
     val sheetId: Long
     ```

2. **Index Strategy**
   - **Lesson:** Proper indexing is critical for MongoDB performance
   - **Solution:** Created indexes on `sourceCellId`, `targetCellId`, and compound index on both
   - **Best Practice:** Monitor query performance and adjust indexes accordingly

3. **Batch Operations**
   - **Lesson:** Batch operations significantly improve MongoDB performance
   - **Solution:** Implemented bulk write operations for dependency updates
   - **Best Practice:** Use BulkOperations for multiple document operations

## Formula Evaluation

1. **Expression Parsing**
   - **Lesson:** Robust formula parsing requires careful error handling
   - **Solution:** Implemented detailed error reporting for formula parsing errors
   - **Best Practice:** Provide clear error messages with position information

2. **Dependency Tracking**
   - **Lesson:** Accurate dependency tracking is essential for formula evaluation
   - **Solution:** Implemented dependency extraction during formula parsing
   - **Best Practice:** Update dependencies whenever formulas change

3. **Evaluation Performance**
   - **Lesson:** Formula evaluation can be performance-intensive
   - **Solution:** Implemented caching of intermediate results and asynchronous updates
   - **Best Practice:** Use a combination of caching and async processing for optimal performance

## Best Practices

### Database Design
1. **Schema Versioning**
   - Always use Flyway for schema changes
   - Never modify production schema directly
   - Test migrations thoroughly before deployment

2. **Naming Conventions**
   - Use snake_case for database identifiers
   - Be consistent with naming across the application
   - Document any naming conventions in the project documentation

### Development Workflow
1. **Local Development**
   - Use Docker for consistent database environments
   - Document all required environment variables
   - Include database initialization in the project setup guide

2. **Code Organization**
   - Keep migration files organized by feature or component
   - Document database schema decisions in the codebase
   - Use meaningful commit messages for database changes

## Common Pitfalls & Solutions

1. **Flyway Migration Issues**
   - Problem: Migrations not found
     - Solution: Check the `locations` configuration in `build.gradle.kts`
   - Problem: Migration checksum mismatch
     - Solution: Never modify applied migrations, create a new one instead

2. **JOOQ Code Generation**
   - Problem: Missing tables in generated code
     - Solution: Check `includes`/`excludes` patterns in JOOQ configuration
   - Problem: Type mismatches
     - Solution: Configure custom data type bindings if needed

3. **Build Configuration**
   - Problem: Build fails with configuration errors
     - Solution: Check for syntax errors in `build.gradle.kts`
   - Problem: Inconsistent dependency versions
     - Solution: Use Gradle's dependency constraints or BOMs

4. **PostgreSQL Enum Type Issues**
   - Problem: Cannot add values to enum types in a transaction
     - Solution: Recreate the enum type with all values
   - Problem: Type mismatch when passing string values to enum columns
     - Solution: Use explicit casting with `?::enum_type` syntax

5. **Foreign Key Constraint Violations**
   - Problem: Cannot insert records with non-existent foreign keys
     - Solution: Remove constraints for development or ensure referenced records exist
   - Problem: Constraint violations during testing
     - Solution: Use test data that satisfies constraints or mock repository layer

6. **ID Type Mismatches**
   - Problem: Domain model uses UUID while database uses BIGINT
     - Solution: Implement conversion logic in repository layer
   - Problem: Inconsistent ID generation
     - Solution: Standardize ID generation approach across repositories

## Recommendations

1. **Database Schema Design**
   - Document enum types and their values in both code and documentation
   - Consider the implications of foreign key constraints on development workflow
   - Plan for schema evolution with careful migration strategies

2. **Repository Implementation**
   - Implement consistent error handling and logging across all repositories
   - Document type conversions and ID generation strategies
   - Add robust unit tests for repository methods

3. **Service Layer Design**
   - Implement graceful degradation for features that depend on database schema
   - Document temporary workarounds and plans for proper implementation
   - Consider feature flags for enabling/disabling features during development

4. **Documentation**
   - Document all database schema decisions
   - Keep an up-to-date ER diagram
   - Document any non-obvious JOOQ usage patterns

5. **Testing**
   - Write integration tests for database operations
   - Test migrations in a CI/CD pipeline
   - Include database state in test fixtures

6. **Performance**
   - Monitor query performance
   - Add appropriate indexes
   - Consider connection pooling configuration

---
*This document will be updated as new lessons are learned throughout the project.*
