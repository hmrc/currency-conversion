/*
 * Copyright 2022 HM Revenue & Customs
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

import com.codahale.metrics.SharedMetricRegistries
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.currencyconversion.repositories.ExchangeRateRepository
import play.api.Application
import uk.gov.hmrc.currencyconversion.models.ExchangeRateObject

import java.time.LocalDate
import scala.concurrent.Future._
import scala.language.postfixOps
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.mvc.Result

import scala.concurrent.Future

class ExchangeRateControllerSpec
    extends AnyWordSpecLike
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach {

  private lazy val exchangeRateRepository = mock[ExchangeRateRepository]

  override def beforeEach(): Unit = {
    Mockito.reset(exchangeRateRepository)
    val exchangeRate: ExchangeRateObject = ExchangeRateObject("exrates-monthly-0919", Json.parse(data).as[JsObject])
    doReturn(Future.successful(true)) when exchangeRateRepository isDataPresent "exrates-monthly-0919"
    doReturn(successful(Some(exchangeRate))) when exchangeRateRepository get "exrates-monthly-0919"

    SharedMetricRegistries.clear()
  }

  override lazy val app: Application = {

    import play.api.inject._

    new GuiceApplicationBuilder()
      .overrides(
        bind[ExchangeRateRepository].toInstance(exchangeRateRepository)
      )
      .build()
  }

  val data: String =
    """{
      |        "timestamp" : "2019-06-28T13:17:21Z",
      |        "correlationid" : "c4a81105-9417-4080-9cd2-c4469efc965c",
      |        "exchangeRates" : [
      |            {
      |                "validFrom" : "2019-09-01",
      |                "validTo" : "2019-09-30",
      |                "currencyCode" : "USD",
      |                "exchangeRate" : 1.213,
      |                "currencyName" : "United State"
      |            },
      |            {
      |                "validFrom" : "2019-09-01",
      |                "validTo" : "2019-09-30",
      |                "currencyCode" : "INR",
      |                "exchangeRate" : 1,
      |                "currencyName" : "India"
      |            }
      |        ]
      |}""".stripMargin

  "Getting rates for a valid date and 1 valid currency" must {

    "return 200 and the correct json" in {

      val result = await(route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-10?cc=USD")).get)

      result.header.status shouldBe OK

      result.header.headers.get("Warning") shouldBe None

      contentAsJson(Future.successful(result)) shouldBe Json.arr(
        Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "USD", "rate" -> "1.213")
      )
      Thread.sleep(2000.toLong)
    }
    "return 200 and the correct json with scaling 2 decimal at least" in {

      val result: Result = await(route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-10?cc=INR")).get)

      result.header.status shouldBe OK

      result.header.headers.get("Warning") shouldBe None

      contentAsJson(Future.successful(result)) shouldBe Json.arr(
        Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "INR", "rate" -> "1.00")
      )
      Thread.sleep(2000.toLong)
    }
  }

  "Getting rates for a valid date and 1 invalid currency" must {

    "return 200 and the correct json" in {

      val result = await(route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-10?cc=INVALID")).get)

      result.header.status shouldBe OK

      result.header.headers.get("Warning") shouldBe None

      contentAsJson(Future.successful(result)) shouldBe Json.arr(
        Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "INVALID")
      )
    }
  }

  "Getting rates for a valid date and 1 valid currency and 1 invalid currency" must {

    "return 200 and the correct json" in {

      val result = await(route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-10?cc=USD&cc=INVALID")).get)

      result.header.status shouldBe OK

      result.header.headers.get("Warning") shouldBe None

      contentAsJson(Future.successful(result)).as[JsArray].value(0).as[JsObject].keys shouldBe Set(
        "startDate",
        "endDate",
        "currencyCode",
        "rate"
      )
      contentAsJson(Future.successful(result)).as[JsArray].value(1).as[JsObject].keys shouldBe Set(
        "startDate",
        "endDate",
        "currencyCode"
      )

    }
  }

  "Getting rates for a date which has no rates Json file and a valid currency code" must {

    "return 200 and the correct json" in {
      doReturn(Future.successful(false)) when exchangeRateRepository isDataPresent "exrates-monthly-1019"

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-10-10?cc=USD")).get

      status(result) shouldBe Status.OK

      contentAsJson(result).as[JsArray].value(0).as[JsObject].keys shouldBe Set(
        "startDate",
        "endDate",
        "currencyCode",
        "rate"
      )
    }
  }

  "Getting rates for a date which has no rates Json file, 1 valid currency code and 1 invalid currency code" must {
    "return response from previous month" in {
      doReturn(Future.successful(false)) when exchangeRateRepository isDataPresent "exrates-monthly-1019"
      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-10-10?cc=USD&cc=INVALID")).get

      status(result) shouldBe Status.OK
    }
  }

  "Getting rates for last day of the month and 1 valid currency code" must {

    "return 200 and the correct json which gets rate from file of the same month" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-30?cc=USD")).get

      status(result) shouldBe Status.OK

      contentAsJson(result) shouldBe Json.arr(
        Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "USD", "rate" -> "1.213")
      )
    }
  }

  "Getting rates for first day of the month and 1 valid currency code" must {

    "return 200 and the correct json which gets rate from file of the same month" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-01?cc=USD")).get

      status(result) shouldBe Status.OK

      contentAsJson(result) shouldBe Json.arr(
        Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "USD", "rate" -> "1.213")
      )
    }
  }

  "Getting rates for an invalid date" must {

    "return 400 and the correct json" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/INVALID-DATE?cc=USD")).get

      status(result) shouldBe Status.BAD_REQUEST

      contentAsJson(result) shouldBe Json.obj("statusCode" -> 400, "message" -> "bad request, cause: REDACTED")

    }
  }

  "Getting currencies for a valid date" must {

    "return 200 and the correct json" in {

      val exchangeRate: ExchangeRateObject = ExchangeRateObject("exrates-monthly-0919", Json.parse(data).as[JsObject])
      doReturn(Future.successful(true)) when exchangeRateRepository isDataPresent "exrates-monthly-0919"
      doReturn(successful(Some(exchangeRate))) when exchangeRateRepository get "exrates-monthly-0919"

      val result = route(app, FakeRequest("GET", "/currency-conversion/currencies/2019-09-01")).get

      status(result) shouldBe Status.OK

      contentAsJson(result).as[JsObject].keys shouldBe Set("start", "end", "currencies")
    }
  }

  "Getting currencies for an invalid date" must {

    "return 400" in {

      val result = route(app, FakeRequest("GET", "/currency-conversion/currencies/INVALID-DATE")).get

      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "Getting currencies for a date which does not exist" must {

    "return 200 if fallback is available" in {

      val exchangeRate: ExchangeRateObject = ExchangeRateObject("exrates-monthly-0919", Json.parse(data).as[JsObject])
      doReturn(Future.successful(true)) when exchangeRateRepository isDataPresent "exrates-monthly-0919"
      doReturn(successful(Some(exchangeRate))) when exchangeRateRepository get "exrates-monthly-0919"

      val date = LocalDate.of(2019.toInt, 9.toInt, 22.toInt)

      val result = route(app, FakeRequest("GET", s"/currency-conversion/currencies/$date")).get

      status(result) shouldBe Status.OK

      contentAsJson(result).as[JsObject].keys shouldBe Set("start", "end", "currencies")
    }
  }

}
