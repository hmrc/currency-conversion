/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.any
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.currencyconversion.errors.XrsFileNotFoundError
import uk.gov.hmrc.currencyconversion.models.ExchangeRateObject
import uk.gov.hmrc.currencyconversion.repositories.{ConversionRatePeriodJson, ExchangeRateRepository}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.Future._
import scala.concurrent.{ExecutionContext, Future}

class ExchangeRateControllerSpec
    extends AnyWordSpecLike
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach {

  implicit val ec = ExecutionContext.global

  private lazy val exchangeRateRepository                                 = mock[ExchangeRateRepository]
  private lazy val mockConversionRatePeriodJson: ConversionRatePeriodJson = mock[ConversionRatePeriodJson]

  def exchangeRateDataJson(id: String, startDate: String, endDate: String): JsObject =
    Json
      .parse(
        s"""
           |{
           |    "_id" : "$id",
           |    "timestamp": "2019-06-28T13:17:21Z",
           |    "correlationid": "c4a81105-9417-4080-9cd2-c4469efc965c",
           |    "exchangeRates": [
           |        {
           |            "validFrom": "$startDate",
           |            "validTo": "$endDate",
           |            "currencyCode": "USD",
           |            "exchangeRate": 1.337,
           |            "currencyName": "US Dollars"
           |        }
           |    ]
           |}
    """.stripMargin
      )
      .as[JsObject]

  val data: JsObject =
    Json
      .parse("""{
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
          |}""".stripMargin)
      .as[JsObject]

  override def beforeEach(): Unit = {
    Mockito.reset(exchangeRateRepository)
    val exchangeRate: ExchangeRateObject = ExchangeRateObject("exrates-monthly-0919", data)
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

  "Getting rates for a valid date and 1 valid currency" when {

    "the file date is the same as the current date" must {

      "return 200 and the correct json" in {

        val result = await(route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-10?cc=USD")).get)

        result.header.status shouldBe OK

        result.header.headers.get("Warning") shouldBe None

        contentAsJson(Future.successful(result)) shouldBe Json.arr(
          Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "USD", "rate" -> "1.213")
        )
      }

      "return 200 and the correct json with scaling 2 decimal at least" in {

        val result: Result = await(route(app, FakeRequest("GET", "/currency-conversion/rates/2019-09-10?cc=INR")).get)

        result.header.status shouldBe OK

        result.header.headers.get("Warning") shouldBe None

        contentAsJson(Future.successful(result)) shouldBe Json.arr(
          Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "INR", "rate" -> "1.00")
        )
      }
    }

    "the file date is old but within the fall back period" must {

      "return 200 and the correct json" in {

        when(exchangeRateRepository.get("exrates-monthly-1219")).thenReturn(Future(None))
        when(exchangeRateRepository.get("exrates-monthly-1119")).thenReturn(Future(None))
        when(exchangeRateRepository.get("exrates-monthly-1019")).thenReturn(Future(None))
        when(exchangeRateRepository.get("exrates-monthly-0919"))
          .thenReturn(
            Future(
              Some(
                ExchangeRateObject(
                  fileName = "exrates-monthly-0919",
                  exchangeRateData = exchangeRateDataJson("exrates-monthly-0919", "2019-09-01", "2019-09-30")
                )
              )
            )
          )

        when(mockConversionRatePeriodJson.getExchangeRateObjectFile(any())).thenReturn(
          Future.successful(
            Right(
              ExchangeRateObject(
                fileName = "exrates-monthly-0919",
                exchangeRateData = exchangeRateDataJson("exrates-monthly-0919", "2019-09-01", "2019-09-30")
              )
            )
          )
        )

        when(mockConversionRatePeriodJson.getConversionRatePeriod(any()))
          .thenReturn(
            Future(Left(XrsFileNotFoundError))
          )

        val result = await(route(app, FakeRequest("GET", "/currency-conversion/rates/2019-12-10?cc=USD")).get)

        result.header.status shouldBe OK

        val dateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z"))

        result.header.headers.get("Warning") shouldBe Some(s"299 - Date out of range: $dateTime")

        contentAsJson(Future.successful(result)) shouldBe
          Json.arr(
            Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "USD", "rate" -> "1.337")
          )
      }

      "return 200 and the correct json with scaling 2 decimal at least" in {

        when(exchangeRateRepository.get("exrates-monthly-1219")).thenReturn(
          Future.successful(
            Some(
              ExchangeRateObject(
                fileName = "exrates-monthly-0919",
                exchangeRateData = data
              )
            )
          )
        )

        when(mockConversionRatePeriodJson.getExchangeRateObjectFile(any())).thenReturn(
          Future.successful(
            Right(
              ExchangeRateObject(
                fileName = "exrates-monthly-0919",
                exchangeRateData = exchangeRateDataJson("exrates-monthly-0919", "2019-09-01", "2019-09-30")
              )
            )
          )
        )

        val result: Result = await(route(app, FakeRequest("GET", "/currency-conversion/rates/2019-12-10?cc=INR")).get)

        result.header.status shouldBe OK

        result.header.headers.get("Warning") shouldBe None

        contentAsJson(Future.successful(result)) shouldBe
          Json.arr(
            Json.obj("startDate" -> "2019-09-01", "endDate" -> "2019-09-30", "currencyCode" -> "INR", "rate" -> "1.00")
          )
      }
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

      when(exchangeRateRepository.get(any())).thenReturn(
        Future.successful(
          Some(
            ExchangeRateObject(
              fileName = "exrates-monthly-0919",
              exchangeRateData = exchangeRateDataJson("exrates-monthly-0919", "2019-09-01", "2019-09-30")
            )
          )
        )
      )

      val result = route(app, FakeRequest("GET", "/currency-conversion/rates/2019-10-10?cc=USD")).get

      status(result) shouldBe Status.OK

      contentAsJson(result).as[JsArray].value(0).as[JsObject].keys shouldBe
        Set(
          "startDate",
          "endDate",
          "currencyCode",
          "rate"
        )
    }
  }

  "Getting rates for a date which has no rates Json file, 1 valid currency code and 1 invalid currency code" must {

    "return response from previous month" in {

      when(exchangeRateRepository.isDataPresent("exrates-monthly-1019")).thenReturn(Future.successful(false))

      when(exchangeRateRepository.get(any())).thenReturn(
        Future.successful(
          Some(
            ExchangeRateObject(
              fileName = "exrates-monthly-0919",
              exchangeRateData = exchangeRateDataJson("exrates-monthly-0919", "2019-09-01", "2019-09-30")
            )
          )
        )
      )

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

      val exchangeRate: ExchangeRateObject = ExchangeRateObject("exrates-monthly-0919", data)
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

      val exchangeRate: ExchangeRateObject = ExchangeRateObject("exrates-monthly-0919", data)
      doReturn(Future.successful(true)) when exchangeRateRepository isDataPresent "exrates-monthly-0919"
      doReturn(successful(Some(exchangeRate))) when exchangeRateRepository get "exrates-monthly-0919"

      val result = route(app, FakeRequest("GET", "/currency-conversion/currencies/2019-09-22")).get

      status(result) shouldBe Status.OK

      contentAsJson(result).as[JsObject].keys shouldBe Set("start", "end", "currencies")
    }

    "return 404 NOT_FOUND if fallback is available" in {

      when(exchangeRateRepository.get("exrates-monthly-0919")).thenReturn(Future(None))
      when(exchangeRateRepository.get("exrates-monthly-0819")).thenReturn(Future(None))
      when(exchangeRateRepository.get("exrates-monthly-0719")).thenReturn(Future(None))
      when(exchangeRateRepository.get("exrates-monthly-0619")).thenReturn(Future(None))
      when(exchangeRateRepository.get("exrates-monthly-0519")).thenReturn(Future(None))
      when(exchangeRateRepository.get("exrates-monthly-0419")).thenReturn(Future(None))
      when(exchangeRateRepository.get("exrates-monthly-0319")).thenReturn(Future(None))

      val result = route(app, FakeRequest("GET", "/currency-conversion/currencies/2019-09-22")).get

      status(result) shouldBe Status.NOT_FOUND
    }
  }
}
