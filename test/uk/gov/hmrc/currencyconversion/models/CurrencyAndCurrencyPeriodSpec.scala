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

class CurrencyAndCurrencyPeriodSpec extends AnyWordSpec with Matchers {

  "Currency" should {
    "serialize to JSON" in {
      val currency     = Currency("United Kingdom", "Pound Sterling", "GBP")
      val expectedJson = Json.parse("""{
                                      | "countryName": "United Kingdom",
                                      | "currencyName": "Pound Sterling",
                                      | "currencyCode": "GBP"
                                      |}""".stripMargin)

      Json.toJson(currency) shouldBe expectedJson
    }

    "deserialize from JSON" in {
      val json             = Json.parse("""{
                              | "countryName": "United Kingdom",
                              | "currencyName": "Pound Sterling",
                              | "currencyCode": "GBP"
                              |}""".stripMargin)
      val expectedCurrency = Currency("United Kingdom", "Pound Sterling", "GBP")

      json.validate[Currency] shouldBe JsSuccess(expectedCurrency)
    }

    "fail deserialization with invalid JSON" in {
      val invalidJson = Json.parse("""{
                                     | "country": "United Kingdom",
                                     | "currency": "Pound Sterling"
                                     |}""".stripMargin)

      invalidJson.validate[Currency] shouldBe a[JsError]
    }
  }

  "CurrencyPeriod" should {
    "serialize to JSON" in {
      val currency1    = Currency("United Kingdom", "Pound Sterling", "GBP")
      val currency2    = Currency("United States", "US Dollar", "USD")
      val period       = CurrencyPeriod(
        start = LocalDate.of(2023, 1, 1),
        end = LocalDate.of(2023, 12, 31),
        currencies = Seq(currency1, currency2)
      )
      val expectedJson = Json.parse("""{
                                      | "start": "2023-01-01",
                                      | "end": "2023-12-31",
                                      | "currencies": [
                                      |   {"countryName": "United Kingdom", "currencyName": "Pound Sterling", "currencyCode": "GBP"},
                                      |   {"countryName": "United States", "currencyName": "US Dollar", "currencyCode": "USD"}
                                      | ]
                                      |}""".stripMargin)

      Json.toJson(period) shouldBe expectedJson
    }

    "deserialize from JSON" in {
      val json           = Json.parse("""{
                              | "start": "2023-01-01",
                              | "end": "2023-12-31",
                              | "currencies": [
                              |   {"countryName": "United Kingdom", "currencyName": "Pound Sterling", "currencyCode": "GBP"},
                              |   {"countryName": "United States", "currencyName": "US Dollar", "currencyCode": "USD"}
                              | ]
                              |}""".stripMargin)
      val currency1      = Currency("United Kingdom", "Pound Sterling", "GBP")
      val currency2      = Currency("United States", "US Dollar", "USD")
      val expectedPeriod = CurrencyPeriod(
        start = LocalDate.of(2023, 1, 1),
        end = LocalDate.of(2023, 12, 31),
        currencies = Seq(currency1, currency2)
      )

      json.validate[CurrencyPeriod] shouldBe JsSuccess(expectedPeriod)
    }

    "fail deserialization with invalid JSON" in {
      val invalidJson = Json.parse("""{
                                     | "start": "2023-01-01",
                                     | "currencies": []
                                     |}""".stripMargin)

      invalidJson.validate[CurrencyPeriod] shouldBe a[JsError]
    }

    "handle an empty currencies list" in {
      val period       = CurrencyPeriod(
        start = LocalDate.of(2023, 1, 1),
        end = LocalDate.of(2023, 12, 31),
        currencies = Seq.empty
      )
      val expectedJson = Json.parse("""{
                                      | "start": "2023-01-01",
                                      | "end": "2023-12-31",
                                      | "currencies": []
                                      |}""".stripMargin)

      Json.toJson(period) shouldBe expectedJson
    }
  }
}
