package com.stress.test.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.UUID
import scala.language.postfixOps

class PaymentInitiateSimulation extends Simulation {
  
  // Base URL and common headers
  val httpProtocol = http
    .baseUrl("http://localhost:8080/api/v1")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("StressTest/1.0")
    .header("userId", "test-user-123")
  
  // UPI Payment Scenario (Google Pay)
  val upiPaymentScenario = scenario("UPI Payment Initiate")
    .exec(
      http("UPI Payment Request")
        .post("/payment/initiate")
        .header("Idempotency-Key", session => UUID.randomUUID().toString)
        .body(StringBody("""
          {
            "amount": 1000.00,
            "paymentMode": "UPI",
            "paymentType": "GOOGLE_PAY",
            "currency": "INR",
            "metadata": {
              "upiId": "user@okaxis",
              "provider": "GOOGLE_PAY",
              "deviceId": "android-12345"
            }
          }
        """))
        .check(status.is(200))
    )
  
  // Credit Card Payment Scenario
  val creditCardPaymentScenario = scenario("Credit Card Payment Initiate")
    .exec(
      http("Credit Card Payment Request")
        .post("/payment/initiate")
        .header("Idempotency-Key", session => UUID.randomUUID().toString)
        .body(StringBody("""
          {
            "amount": 2500.00,
            "paymentMode": "CREDIT_CARD",
            "paymentType": "VISA",
            "currency": "INR",
            "metadata": {
              "cardNumber": "XXXX-XXXX-XXXX-1234",
              "cardNetwork": "VISA",
              "expiryMonth": "12",
              "expiryYear": "2025"
            }
          }
        """))
        .check(status.is(200))
    )
  
  // Debit Card Payment Scenario
  val debitCardPaymentScenario = scenario("Debit Card Payment Initiate")
    .exec(
      http("Debit Card Payment Request")
        .post("/payment/initiate")
        .header("Idempotency-Key", session => UUID.randomUUID().toString)
        .body(StringBody("""
          {
            "amount": 1500.00,
            "paymentMode": "DEBIT_CARD",
            "paymentType": "MASTERCARD",
            "currency": "INR",
            "metadata": {
              "cardNumber": "XXXX-XXXX-XXXX-5678",
              "cardNetwork": "MASTERCARD",
              "expiryMonth": "10",
              "expiryYear": "2024"
            }
          }
        """))
        .check(status.is(200))
    )
  
  // Test setup with weighted scenarios - using smaller numbers for testing
  setUp(
    upiPaymentScenario.inject(
      constantUsersPerSec(5) during(30.seconds)
    ).protocols(httpProtocol),
    
    creditCardPaymentScenario.inject(
      constantUsersPerSec(3) during(30.seconds)
    ).protocols(httpProtocol),
    
    debitCardPaymentScenario.inject(
      constantUsersPerSec(2) during(30.seconds)
    ).protocols(httpProtocol)
  ).maxDuration(60.seconds)
}
