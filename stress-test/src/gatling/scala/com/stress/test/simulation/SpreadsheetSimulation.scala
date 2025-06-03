package com.stress.test.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.UUID
import scala.language.postfixOps
import scala.util.Random

class SpreadsheetSimulation extends Simulation {
  
  // Base URL and common headers
  val httpProtocol = http
    .baseUrl("http://localhost:8080/v1")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("StressTest/1.0")
  
  // Feeder for user IDs - use consistent user IDs to ensure proper access
  val userIdFeeder = Iterator.continually {
    val userId = Random.nextInt(5) + 1 // Use a small set of user IDs (1-5)
    Map("userId" -> userId.toString)
  }
  
  // Feeder for cell coordinates
  val cellFeeder = Iterator.continually {
    val row = Random.nextInt(20) + 1
    val colNum = Random.nextInt(20) + 1
    val colLetter = (colNum + 64).toChar.toString
    Map(
      "row" -> row.toString,
      "column" -> colLetter,
      "data" -> s"Test data ${UUID.randomUUID().toString.substring(0, 8)}"
    )
  }
  
  // Feeder for fixed cell coordinates (for concurrency tests)
  val fixedCellFeeder = Iterator.continually {
    // Use a limited set of cells to ensure concurrent updates
    val rowOptions = List(1, 2, 3, 4, 5)
    val colOptions = List("A", "B", "C", "D", "E")
    
    val row = rowOptions(Random.nextInt(rowOptions.size))
    val column = colOptions(Random.nextInt(colOptions.size))
    
    Map(
      "row" -> row.toString,
      "column" -> column,
      "data" -> s"Concurrent update ${UUID.randomUUID().toString.substring(0, 8)}"
    )
  }
  
  // Feeder for cell expressions
  val expressionFeeder = Iterator.continually {
    val row = Random.nextInt(20) + 1
    val colNum = Random.nextInt(20) + 1
    val colLetter = (colNum + 64).toChar.toString
    
    // Generate random expression types
    val expressionType = Random.nextInt(4)
    val expression = expressionType match {
      case 0 => s"=SUM(A1:${(Random.nextInt(5) + 65).toChar}${Random.nextInt(5) + 1})"
      case 1 => s"=AVERAGE(A1:${(Random.nextInt(5) + 65).toChar}${Random.nextInt(5) + 1})"
      case 2 => s"=MIN(A1:${(Random.nextInt(5) + 65).toChar}${Random.nextInt(5) + 1})"
      case 3 => s"=MAX(A1:${(Random.nextInt(5) + 65).toChar}${Random.nextInt(5) + 1})"
    }
    
    Map(
      "row" -> row.toString,
      "column" -> colLetter,
      "data" -> expression
    )
  }
  
  // Feeder for fixed cell expressions (for concurrency tests)
  val fixedExpressionFeeder = Iterator.continually {
    // Use a limited set of cells to ensure concurrent updates
    val rowOptions = List(6, 7, 8, 9, 10)
    val colOptions = List("A", "B", "C", "D", "E")
    
    val row = rowOptions(Random.nextInt(rowOptions.size))
    val column = colOptions(Random.nextInt(colOptions.size))
    
    // Reference cells that might be updated concurrently
    val refRow = Random.nextInt(5) + 1
    val refCol = (Random.nextInt(5) + 65).toChar
    
    // Generate expressions that reference other cells being updated
    val expressionType = Random.nextInt(4)
    val expression = expressionType match {
      case 0 => s"=SUM(A1:${refCol}${refRow})"
      case 1 => s"=AVERAGE(A1:${refCol}${refRow})"
      case 2 => s"=MIN(A1:${refCol}${refRow})"
      case 3 => s"=MAX(A1:${refCol}${refRow})"
    }
    
    Map(
      "row" -> row.toString,
      "column" -> column,
      "data" -> expression
    )
  }
  
  // Create Sheet Scenario
  val createSheetScenario = scenario("Create Sheet")
    .feed(userIdFeeder)
    .exec(
      http("Create Sheet Request")
        .post("/sheet")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "name": "Stress Test Sheet #{userId}",
            "description": "Sheet created during stress test",
            "maxRows": 100,
            "maxColumns": 100
          }
        """.stripMargin))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("sheetId"))
    )
    .exec(session => {
      println(s"Created sheet ${session("sheetId").as[String]} for user ${session("userId").as[String]}")
      session
    })
  
  // Full workflow scenario - create sheet and perform operations on it
  val fullWorkflowScenario = scenario("Full Workflow")
    .feed(userIdFeeder)
    // Create a sheet first
    .exec(
      http("Create Sheet")
        .post("/sheet")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "name": "Stress Test Sheet #{userId}",
            "description": "Sheet created during stress test",
            "maxRows": 100,
            "maxColumns": 100
          }
        """.stripMargin))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("sheetId"))
    )
    // Get sheet details
    .exec(
      http("Get Sheet Details")
        .get("/sheet/#{sheetId}")
        .header("X-User-ID", "#{userId}")
        .check(status.is(200))
    )
    // Update cells with values
    .repeat(3) {
      feed(cellFeeder)
      .exec(
        http("Update Cell Value")
          .post("/sheet/#{sheetId}/cell")
          .header("X-User-ID", "#{userId}")
          .body(StringBody("""
            {
              "row": #{row},
              "column": "#{column}",
              "data": "#{data}"
            }
          """.stripMargin))
          .check(status.in(200, 400, 409))
      )
    }
    // Get all cells
    .exec(
      http("Get All Cells")
        .get("/sheet/#{sheetId}/cell")
        .header("X-User-ID", "#{userId}")
        .check(status.is(200))
    )
    // Update a cell with an expression
    .feed(expressionFeeder)
    .exec(
      http("Update Cell Expression")
        .post("/sheet/#{sheetId}/cell")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "row": #{row},
            "column": "#{column}",
            "data": "#{data}"
          }
        """.stripMargin))
        .check(status.in(200, 400, 409))
    )
    // Get a specific cell
    .feed(cellFeeder)
    .exec(
      http("Get Cell")
        .get("/sheet/#{sheetId}/cell/#{row}/#{column}")
        .header("X-User-ID", "#{userId}")
        .check(status.in(200, 404))
    )
    // Delete a cell
    .feed(cellFeeder)
    .exec(
      http("Delete Cell")
        .delete("/sheet/#{sheetId}/cell/#{row}/#{column}")
        .header("X-User-ID", "#{userId}")
        .check(status.in(204, 404))
    )
    // Share the sheet
    .exec(
      http("Share Sheet")
        .post("/sheet/#{sheetId}/share")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "userIds": [2, 3, 4],
            "accessType": "READ"
          }
        """.stripMargin))
        .check(status.in(200, 403, 404))
    )
    // Get user sheets
    .exec(
      http("Get User Sheets")
        .get("/sheet")
        .header("X-User-ID", "#{userId}")
        .check(status.is(200))
    )
  
  // Concurrency test for primitive value updates
  val concurrentPrimitiveUpdatesScenario = scenario("Concurrent Primitive Updates")
    .feed(userIdFeeder)
    // Create a shared sheet for all users
    .exec(
      http("Create Shared Sheet")
        .post("/sheet")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "name": "Concurrency Test Sheet",
            "description": "Sheet for testing concurrent updates",
            "maxRows": 100,
            "maxColumns": 100
          }
        """.stripMargin))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("sharedSheetId"))
    )
    // Share with all test users
    .exec(
      http("Share With All Users")
        .post("/sheet/#{sharedSheetId}/share")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "userIds": [1, 2, 3, 4, 5],
            "accessType": "WRITE"
          }
        """.stripMargin))
        .check(status.in(200, 403, 404))
    )
    // Repeatedly update the same cells with different values
    .repeat(10) {
      feed(fixedCellFeeder)
      .exec(
        http("Concurrent Primitive Update")
          .post("/sheet/#{sharedSheetId}/cell")
          .header("X-User-ID", "#{userId}")
          .body(StringBody("""
            {
              "row": #{row},
              "column": "#{column}",
              "data": "#{data}"
            }
          """.stripMargin))
          .check(status.in(200, 400, 409))
      )
      .pause(100.milliseconds) // Small pause to create interleaving updates
    }
    // Get the final cell values
    .exec(
      http("Get Final Cell Values")
        .get("/sheet/#{sharedSheetId}/cell")
        .header("X-User-ID", "#{userId}")
        .check(status.is(200))
    )
  
  // Concurrency test for expression updates
  val concurrentExpressionUpdatesScenario = scenario("Concurrent Expression Updates")
    .feed(userIdFeeder)
    // Create a shared sheet for all users
    .exec(
      http("Create Expression Test Sheet")
        .post("/sheet")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "name": "Expression Concurrency Test Sheet",
            "description": "Sheet for testing concurrent expression updates",
            "maxRows": 100,
            "maxColumns": 100
          }
        """.stripMargin))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("expressionSheetId"))
    )
    // Share with all test users
    .exec(
      http("Share Expression Sheet")
        .post("/sheet/#{expressionSheetId}/share")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "userIds": [1, 2, 3, 4, 5],
            "accessType": "WRITE"
          }
        """.stripMargin))
        .check(status.in(200, 403, 404))
    )
    // First populate some cells with primitive values
    .repeat(5) {
      feed(fixedCellFeeder)
      .exec(
        http("Populate Base Values")
          .post("/sheet/#{expressionSheetId}/cell")
          .header("X-User-ID", "#{userId}")
          .body(StringBody("""
            {
              "row": #{row},
              "column": "#{column}",
              "data": "#{userId}0"
            }
          """.stripMargin))
          .check(status.in(200, 400, 409))
      )
    }
    // Then concurrently update cells with expressions that reference those cells
    .repeat(10) {
      feed(fixedExpressionFeeder)
      .exec(
        http("Concurrent Expression Update")
          .post("/sheet/#{expressionSheetId}/cell")
          .header("X-User-ID", "#{userId}")
          .body(StringBody("""
            {
              "row": #{row},
              "column": "#{column}",
              "data": "#{data}"
            }
          """.stripMargin))
          .check(status.in(200, 400, 409))
      )
      .pause(100.milliseconds) // Small pause to create interleaving updates
    }
    // Get the final cell values
    .exec(
      http("Get Final Expression Values")
        .get("/sheet/#{expressionSheetId}/cell")
        .header("X-User-ID", "#{userId}")
        .check(status.is(200))
    )
  
  // Circular dependency stress test
  val circularDependencyScenario = scenario("Circular Dependency Test")
    .feed(userIdFeeder)
    // Create a sheet
    .exec(
      http("Create Circular Test Sheet")
        .post("/sheet")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "name": "Circular Dependency Test Sheet",
            "description": "Sheet for testing circular dependencies",
            "maxRows": 100,
            "maxColumns": 100
          }
        """.stripMargin))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("circularSheetId"))
    )
    // Create a circular reference chain: A1 -> B1 -> C1 -> A1
    .exec(
      http("Set A1 to reference C1")
        .post("/sheet/#{circularSheetId}/cell")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "row": 1,
            "column": "A",
            "data": "=C1+1"
          }
        """.stripMargin))
        .check(status.in(200, 400, 409))
    )
    .exec(
      http("Set B1 to reference A1")
        .post("/sheet/#{circularSheetId}/cell")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "row": 1,
            "column": "B",
            "data": "=A1+1"
          }
        """.stripMargin))
        .check(status.in(200, 400, 409))
    )
    .exec(
      http("Set C1 to reference B1 - creating circular dependency")
        .post("/sheet/#{circularSheetId}/cell")
        .header("X-User-ID", "#{userId}")
        .body(StringBody("""
          {
            "row": 1,
            "column": "C",
            "data": "=B1+1"
          }
        """.stripMargin))
        .check(status.is(400)) // Should fail with 400 Bad Request due to circular dependency
    )
  
  // Test setup with all scenarios
  setUp(
    // Run the full workflow for basic functionality
    fullWorkflowScenario.inject(
      rampUsers(10) during(30.seconds)
    ).protocols(httpProtocol),
    
    // Run concurrent primitive updates test
    concurrentPrimitiveUpdatesScenario.inject(
      nothingFor(5.seconds), // Wait for setup
      rampUsers(20) during(20.seconds)
    ).protocols(httpProtocol),
    
    // Run concurrent expression updates test
    concurrentExpressionUpdatesScenario.inject(
      nothingFor(5.seconds), // Wait for setup
      rampUsers(20) during(20.seconds)
    ).protocols(httpProtocol),
    
    // Run circular dependency test
    circularDependencyScenario.inject(
      nothingFor(5.seconds), // Wait for setup
      rampUsers(5) during(10.seconds)
    ).protocols(httpProtocol)
  ).maxDuration(120.seconds)
}
