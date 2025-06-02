---
description: Kotlin Spring Boot Project Setup
---

# 🛠️ Kotlin Spring Boot Project Structure Setup (No Placeholder Files)

## 📌 Context
- This project has already been created using Spring Initializr with Kotlin, Gradle, and Spring Web.
- The goal is to define and create a clean directory structure under the base package (e.g., `com.example.myapp`).

--

---

## 📁 Step 2: Create the Following Subdirectories

Within the base package, create the following directories:

- `config/` – For Spring configuration classes (e.g., WebMvcConfig, SecurityConfig)
- `controller/` – For REST controllers
- `dto/` – For request/response data transfer objects
- `exception/` – For custom exceptions and global error handling
- `model/domain/` – For entity/domain model classes
- `repository/` – For repository interfaces (e.g., JPA/Mongo)
- `service/` – For service interfaces
- `service/impl/` – For implementations of service interfaces
- `util/` – For utility/helper classes

---

## 🚫 Step 3: Do Not Create Any Kotlin Files

> **Important:** Do **not** create any placeholder Kotlin files or classes. Only the directory structure should be set up.

---

## ⚙️ Step 4: Add Kotlin Coroutine Dependencies

Open the `build.gradle.kts` file and add the following dependencies:

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.0")
}