# Task 1: MongoDB and Redis Setup

## Overview
This task involves setting up MongoDB and Redis in the docker-compose.yml file. Redis is already configured, but MongoDB needs to be added.

## Implementation Steps

### 1. Update docker-compose.yml to add MongoDB
- Add MongoDB service configuration to docker-compose.yml
- Configure MongoDB with appropriate environment variables
- Set up volume mapping for data persistence
- Configure health checks
- Expose necessary ports

### Detailed Implementation

```yaml
# MongoDB service to be added to docker-compose.yml
mongodb:
  image: mongo:6.0-focal
  container_name: sheets-mongodb
  environment:
    MONGO_INITDB_ROOT_USERNAME: mongo
    MONGO_INITDB_ROOT_PASSWORD: mongo
    MONGO_INITDB_DATABASE: sheets
  ports:
    - "27017:27017"
  volumes:
    - mongodb_data:/data/db
  healthcheck:
    test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
    interval: 10s
    timeout: 5s
    retries: 5
  restart: unless-stopped
```

### 2. Add MongoDB volume to volumes section
```yaml
volumes:
  postgres_data:
  redis_data:
  mongodb_data:  # Add this line
```

### 3. Update build.gradle.kts to add MongoDB dependencies
- Add Spring Data MongoDB dependency to build.gradle.kts

```kotlin
// MongoDB
implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
```

### 4. Create MongoDB Configuration Class
Create a new file: `src/main/kotlin/com/sheets/config/MongoConfig.kt`

```kotlin
package com.sheets.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@EnableMongoRepositories(basePackages = ["com.sheets.repositories"])
class MongoConfig : AbstractMongoClientConfiguration() {
    override fun getDatabaseName(): String = "sheets"
}
```

### 5. Verify Redis Configuration
- Ensure Redis is properly configured in docker-compose.yml (already done)
- Create Redis configuration class if not already present

Create a new file: `src/main/kotlin/com/sheets/config/RedisConfig.kt`

```kotlin
package com.sheets.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {
    
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val configuration = RedisStandaloneConfiguration("localhost", 6379)
        configuration.setPassword("redispass")
        return LettuceConnectionFactory(configuration)
    }
    
    @Bean
    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = redisConnectionFactory
        template.keySerializer = StringRedisSerializer()
        return template
    }
}
```

### 6. Update application.yml with MongoDB and Redis configuration
Create or update `src/main/resources/application.yml` with the following:

```yaml
spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: sheets
      username: mongo
      password: mongo
  redis:
    host: localhost
    port: 6379
    password: redispass
```

## Testing
1. Start the Docker containers using `docker-compose up -d`
2. Verify MongoDB is running: `docker logs sheets-mongodb`
3. Verify Redis is running: `docker logs sheets-redis`
4. Test MongoDB connection using MongoDB Compass or CLI
5. Test Redis connection using Redis CLI

## Completion Criteria
- MongoDB and Redis containers are running successfully
- Application can connect to both MongoDB and Redis
- Configuration classes are properly set up
- Dependencies are correctly added to build.gradle.kts
