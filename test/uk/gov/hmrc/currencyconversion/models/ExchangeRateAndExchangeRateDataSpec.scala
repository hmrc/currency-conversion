/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.currencyconversion.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsSuccess, Json}
import java.time.LocalDate

class ExchangeRateAndExchangeRateDataSpec extends AnyWordSpec with Matchers {

  "ExchangeRate" should {
    "serialize to JSON" in {
      val exchangeRate = ExchangeRate(
        validFrom = LocalDate.of(2023, 1, 1),
        validTo = LocalDate.of(2023, 12, 31),
        currencyCode = "GBP",
        exchangeRate = BigDecimal(1.2345),
        currencyName = "Pound Sterling"
      )
      val expectedJson = Json.parse("""{
                                      | "validFrom": "2023-01-01",
                                      | "validTo": "2023-12-31",
                                      | "currencyCode": "GBP",
                                      | "exchangeRate": 1.2345,
                                      | "currencyName": "Pound Sterling"
                                      |}""".stripMargin)

      Json.toJson(exchangeRate)(ExchangeRate.writes) shouldBe expectedJson
    }

    "deserialize from JSON" in {
      val json = Json.parse("""{
                              | "validFrom": "2023-01-01",
                              | "validTo": "2023-12-31",
                              | "currencyCode": "GBP",
                              | "exchangeRate": 1.2345,
                              | "currencyName": "Pound Sterling"
                              |}""".stripMargin)
      val expectedExchangeRate = ExchangeRate(
        validFrom = LocalDate.of(2023, 1, 1),
        validTo = LocalDate.of(2023, 12, 31),
        currencyCode = "GBP",
        exchangeRate = BigDecimal(1.2345),
        currencyName = "Pound Sterling"
      )

      json.validate[ExchangeRate](ExchangeRate.reads) shouldBe JsSuccess(expectedExchangeRate)
    }

    "fail deserialization with invalid JSON" in {
      val invalidJson = Json.parse("""{
                                     | "validFrom": "2023-01-01",
                                     | "validTo": "2023-12-31",
                                     | "currencyCode": "GBP"
                                     |}""".stripMargin)

      invalidJson.validate[ExchangeRate](ExchangeRate.reads) shouldBe a[JsError]
    }
  }

  "ExchangeRateData" should {
    "serialize to JSON" in {
      val exchangeRate1 = ExchangeRate(
        validFrom = LocalDate.of(2023, 1, 1),
        validTo = LocalDate.of(2023, 12, 31),
        currencyCode = "GBP",
        exchangeRate = BigDecimal(1.2345),
        currencyName = "Pound Sterling"
      )
      val exchangeRate2 = ExchangeRate(
        validFrom = LocalDate.of(2023, 6, 1),
        validTo = LocalDate.of(2023, 12, 31),
        currencyCode = "USD",
        exchangeRate = BigDecimal(1.5678),
        currencyName = "US Dollar"
      )
      val exchangeRateData = ExchangeRateData(
        timestamp = "2023-01-01T12:34:56Z",
        correlationId = "abc-123",
        exchangeData = Seq(exchangeRate1, exchangeRate2)
      )
      val expectedJson = Json.parse("""{
                                      | "timestamp": "2023-01-01T12:34:56Z",
                                      | "correlationId": "abc-123",
                                      | "exchangeData": [
                                      |   {"validFrom": "2023-01-01", "validTo": "2023-12-31", "currencyCode": "GBP", "exchangeRate": 1.2345, "currencyName": "Pound Sterling"},
                                      |   {"validFrom": "2023-06-01", "validTo": "2023-12-31", "currencyCode": "USD", "exchangeRate": 1.5678, "currencyName": "US Dollar"}
                                      | ]
                                      |}""".stripMargin)

      Json.toJson(exchangeRateData)(ExchangeRateData.writes) shouldBe expectedJson
    }

    "deserialize from JSON" in {
      val json = Json.parse("""{
                              | "timestamp": "2023-01-01T12:34:56Z",
                              | "correlationid": "abc-123",
                              | "exchangeRates": [
                              |   {"validFrom": "2023-01-01", "validTo": "2023-12-31", "currencyCode": "GBP", "exchangeRate": 1.2345, "currencyName": "Pound Sterling"},
                              |   {"validFrom": "2023-06-01", "validTo": "2023-12-31", "currencyCode": "USD", "exchangeRate": 1.5678, "currencyName": "US Dollar"}
                              | ]
                              |}""".stripMargin)
      val exchangeRate1 = ExchangeRate(
        validFrom = LocalDate.of(2023, 1, 1),
        validTo = LocalDate.of(2023, 12, 31),
        currencyCode = "GBP",
        exchangeRate = BigDecimal(1.2345),
        currencyName = "Pound Sterling"
      )
      val exchangeRate2 = ExchangeRate(
        validFrom = LocalDate.of(2023, 6, 1),
        validTo = LocalDate.of(2023, 12, 31),
        currencyCode = "USD",
        exchangeRate = BigDecimal(1.5678),
        currencyName = "US Dollar"
      )
      val expectedExchangeRateData = ExchangeRateData(
        timestamp = "2023-01-01T12:34:56Z",
        correlationId = "abc-123",
        exchangeData = Seq(exchangeRate1, exchangeRate2)
      )

      json.validate[ExchangeRateData](ExchangeRateData.reads) shouldBe JsSuccess(expectedExchangeRateData)
    }

    "handle default empty exchange rates during deserialization" in {
      val json = Json.parse("""{
                              | "timestamp": "2023-01-01T12:34:56Z",
                              | "correlationid": "abc-123"
                              |}""".stripMargin)
      val expectedExchangeRateData = ExchangeRateData(
        timestamp = "2023-01-01T12:34:56Z",
        correlationId = "abc-123",
        exchangeData = Seq.empty
      )

      json.validate[ExchangeRateData](ExchangeRateData.reads) shouldBe JsSuccess(expectedExchangeRateData)
    }

    "fail deserialization with invalid JSON" in {
      val invalidJson = Json.parse("""{
                                     | "timestamp": "2023-01-01T12:34:56Z"
                                     |}""".stripMargin)

      invalidJson.validate[ExchangeRateData](ExchangeRateData.reads) shouldBe a[JsError]
    }
  }
}
