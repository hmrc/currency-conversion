/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.currencyconversion.controllers

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec
import play.api.inject.bind
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

class ExchangeRateControllerSpec extends UnitSpec with GuiceOneAppPerSuite {

  override lazy val app = new GuiceApplicationBuilder().overrides(
    bind[AuditConnector].toInstance(MockitoSugar.mock[AuditConnector])
  ).build()


  "Getting rates for a valid date and 1 valid currency" should {

    "return 200 and the correct json" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-10?cc=USD")).get

      status(result) shouldBe Status.OK

      result.header.headers.get("Warning") shouldBe None

      contentAsJson(result) shouldBe Json.arr(
        Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "USD", "rate" -> "1.213")
      )
    }
  }

  "Getting rates for a valid date and 1 invalid currency" should {

    "return 200 and the correct json" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-10?cc=INVALID")).get


      status(result) shouldBe Status.OK

      result.header.headers.get("Warning") shouldBe None

      contentAsJson(result) shouldBe Json.arr(
        Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "INVALID")
      )
    }
  }

  "Getting rates for a valid date and 1 valid currency and 1 invalid currency" should {

    "return 200 and the correct json" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-10?cc=USD&cc=INVALID")).get

      status(result) shouldBe Status.OK

      result.header.headers.get("Warning") shouldBe None

      contentAsJson(result).as[JsArray].value(0).as[JsObject].keys shouldBe Set("startDate", "endDate", "currencyCode", "rate")
      contentAsJson(result).as[JsArray].value(1).as[JsObject].keys shouldBe Set("startDate", "endDate", "currencyCode")

    }
  }

  "Getting rates for a date which has no rates xml file and a valid currency code" should {

    "return 200 and the correct json" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/3000-03-10?cc=USD")).get

      status(result) shouldBe Status.OK

      contentAsJson(result).as[JsArray].value(0).as[JsObject].keys shouldBe Set("startDate", "endDate", "currencyCode", "rate")
    }
  }

  "Getting rates for a date which has no rates xml file, 1 valid currency code and 1 invalid currency code" should {

    "return 200 and the correct json" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/3000-03-10?cc=USD&cc=INVALID")).get

      status(result) shouldBe Status.OK

      contentAsJson(result).as[JsArray].value(0).as[JsObject].keys shouldBe Set("startDate", "endDate", "currencyCode", "rate")
      contentAsJson(result).as[JsArray].value(1).as[JsObject].keys shouldBe Set("startDate", "endDate", "currencyCode")
    }

    "return a warning in the headers" in {
      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/3000-03-10?cc=USD&cc=INVALID")).get

      result.header.headers("Warning") should startWith("""299 - "Date out of range"""")
    }
  }

  "Getting rates for last day of the month and 1 valid currency code" should {

    "return 200 and the correct json which gets rate from file of the same month" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-30?cc=USD")).get

      status(result) shouldBe Status.OK

      contentAsJson(result) shouldBe Json.arr(
        Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "USD", "rate" -> "1.213")
      )
    }
  }

  "Getting rates for first day of the month and 1 valid currency code" should {

    "return 200 and the correct json which gets rate from file of the same month" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-01?cc=USD")).get

      status(result) shouldBe Status.OK

      contentAsJson(result) shouldBe Json.arr(
        Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "USD", "rate" -> "1.213")
      )
    }
  }

  "Getting rates for an invalid date" should {

    "return 400 and the correct json" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/INVALID-DATE?cc=USD")).get

      status(result) shouldBe Status.BAD_REQUEST

      contentAsJson(result) shouldBe Json.obj("statusCode" -> 400, "message" -> "bad request")

    }
  }
}
