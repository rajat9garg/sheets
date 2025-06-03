This file contains a list of tasks that need to be completed for the project.

BEFORE STARTING ANY TASK MAKE SURE TO READ REQUIREMENTS.md and have the complete context of the current
work and project structure.

IMPORTANT TO KEEP IN MIND
1. THERE IS NO SECURITY TO be implemented in this project. userId comes in the headers of the requests.
2. KEEP building the project after some steps to see if there are any errors in the code and fix them
3. The basic project structure is already in place.
4. EXTENSIBILITY IS THE MOST IMPORTANT THING TO KEEP IN MIND.

Tasks1
- Setup MongoDb (adding docker-compose.yml)
- Setup Redis (adding docker-compose.yml If not done already)
- Setup client and configs
- Use Synchronous libraries and add dependencies to build.gradle.kts

Tasks2
- Sheet Management Setup
- Create DataModels and Repositories for Sheets and Cells (adding build.gradle.kts)
- Setup Flyway (adding build.gradle.kts)
- Setup JOOQ (adding build.gradle.kts)
- GENERATE FLYWAY AND JOOQ CODE
- GENERATE OPENAPI SPEC (GENERATOR IS ALREADY ADDED IN THE PROJECT)
- CREATE SHEET CONTROLLER (adding src/main/kotlin/com/sheets/controllers/SheetController.kt) by implementing generated openAPI interface
- CREATE SHEET SERVICE AND REPOSITORY (adding src/main/kotlin/com/sheets/services/SheetService.kt and src/main/kotlin/com/sheets/repositories/SheetRepository.kt)


TASK3
- CREATE Exception and error response

Task4
- CREATE CELL Domain model and tables in mongoDB
- CREATE CELL Repository with interfaces
- CREATE CELL Controller using OPEN API only
- Change create sheet api whenever a sheet is created the required number of cells

- Update cell flow
  - identify the value in the update api body if this is an expression or not expression starts with "="
  - parse the expression to generate the dependencies list
  - take a lock on the dependencies using redis and then update the cell data in redis
  - update the data in the redis cache

Algorithm to update/add value of a cell with expression
- change the column to be alphabetical instead of a number so the a cell will be represented as A1,A2,A3,B1,B2,B3 AA1

- check first we are getting an expression or not
- If we are getting and expression parse it to figure out the dependencies and which expression is it
- Store the dependencies in cell_dependencies tables in mongoDB
- calculate the value of the expression and store it in the cell table in mongoDB
- Store the cell value in redis with a ttl of 1 hour and cell dependencies also in redis and update the mongoDb async
- check for cyclic dependency for concurrent calls if it is there revoke the operation
- Also tell me how would you solve for deadlocks
- return the value
INPUT: =row + column