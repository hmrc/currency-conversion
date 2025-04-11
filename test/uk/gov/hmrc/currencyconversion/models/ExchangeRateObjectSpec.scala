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

class ExchangeRateObjectSpec extends AnyWordSpec with Matchers {

  "ExchangeRateObject" should {
    "serialize to JSON" in {
      val exchangeRateObject = ExchangeRateObject(
        fileName = "file123",
        exchangeRateData = Json.obj("key1" -> "value1", "key2" -> "value2")
      )
      val expectedJson       = Json.parse("""{
                                      | "_id": "file123",
                                      | "exchangeRateData": {
                                      |   "key1": "value1",
                                      |   "key2": "value2"
                                      | }
                                      |}""".stripMargin)

      Json.toJson(exchangeRateObject)(ExchangeRateObject.writes) shouldBe expectedJson
    }

    "deserialize from JSON" in {
      val json                       = Json.parse("""{
                              | "_id": "file123",
                              | "exchangeRateData": {
                              |   "key1": "value1",
                              |   "key2": "value2"
                              | }
                              |}""".stripMargin)
      val expectedExchangeRateObject = ExchangeRateObject(
        fileName = "file123",
        exchangeRateData = Json.obj("key1" -> "value1", "key2" -> "value2")
      )

      json.validate[ExchangeRateObject](ExchangeRateObject.reads) shouldBe JsSuccess(expectedExchangeRateObject)
    }

    "fail deserialization with missing required fields" in {
      val invalidJson = Json.parse("""{
                                     | "exchangeRateData": {
                                     |   "key1": "value1"
                                     | }
                                     |}""".stripMargin)

      invalidJson.validate[ExchangeRateObject](ExchangeRateObject.reads) shouldBe a[JsError]
    }

    "fail deserialization with invalid field types" in {
      val invalidJson = Json.parse("""{
                                     | "_id": 123,
                                     | "exchangeRateData": "notAnObject"
                                     |}""".stripMargin)

      invalidJson.validate[ExchangeRateObject](ExchangeRateObject.reads) shouldBe a[JsError]
    }

    "handle empty exchangeRateData during deserialization" in {
      val json                       = Json.parse("""{
                              | "_id": "file123",
                              | "exchangeRateData": {}
                              |}""".stripMargin)
      val expectedExchangeRateObject = ExchangeRateObject(
        fileName = "file123",
        exchangeRateData = Json.obj()
      )

      json.validate[ExchangeRateObject](ExchangeRateObject.reads) shouldBe JsSuccess(expectedExchangeRateObject)
    }
  }
}
