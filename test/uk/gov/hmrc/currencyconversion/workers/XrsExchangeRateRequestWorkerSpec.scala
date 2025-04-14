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

package uk.gov.hmrc.currencyconversion.workers

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.mockito.Mockito
import org.mockito.Mockito.{mock, when}
import org.scalatest.OptionValues
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import uk.gov.hmrc.currencyconversion.models.{ExchangeRate, ExchangeRateData}
import uk.gov.hmrc.currencyconversion.repositories.ExchangeRateRepository
import uk.gov.hmrc.currencyconversion.utils.WireMockHelper

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}

class XrsExchangeRateRequestWorkerSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with WireMockHelper
    with Eventually {

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "workers.xrs-exchange-rate.interval" -> "1 second",
      "microservice.services.des.port"     -> server.port()
    )

  private val mockedJsonResponse =
    """{"timestamp":"2021-06-15T15:41:38Z",
      |"correlationid":"72a89d23-0fc6-4212-92fc-ea8b05139c76",
      |"exchangeRates":[{"validFrom":"2021-06-15","validTo":"2021-06-15","currencyCode":"ARS","exchangeRate":133.25,"currencyName":"Peso"}]}""".stripMargin

  private val mockedEmptyExchangeRatesJsonResponse =
    """{"timestamp":"2021-06-15T15:41:38Z",
      |"correlationid":"72a89d23-0fc6-4212-92fc-ea8b05139c76"
      |}""".stripMargin

  private val thisMonth: LocalDate   = LocalDate.now.withDayOfMonth(1)
  private val lastMonth: LocalDate   = LocalDate.now.minusMonths(1)
  private val nextMonth: LocalDate   = LocalDate.now.plusMonths(1)
  private val inTwoMonths: LocalDate = LocalDate.now.plusMonths(2)
  private val year                   = 2022
  private val dayOfMonth             = 26
  private val month                  = 6

  "must call the xrs exchange rate service and receive the response" in {

    server.stubFor(
      post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))
        .willReturn(aResponse().withStatus(OK).withBody(mockedJsonResponse))
    )
    val app = builder.build()
    running(app) {
      val worker = app.injector.instanceOf[XrsExchangeRateRequestWorker]

      val workerResponse = worker.tap.pull().futureValue.value
      workerResponse.status shouldBe OK
      workerResponse.body   shouldBe mockedJsonResponse
    }

  }

  "Handle the service unavailable response from Xrs service" in {

    server.stubFor(
      post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))
        .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE))
    )
    val app = builder.build()
    running(app) {
      val worker = app.injector.instanceOf[XrsExchangeRateRequestWorker]

      val workerResponse = worker.tap.pull().futureValue.value

      workerResponse.status shouldBe SERVICE_UNAVAILABLE
    }
  }

  "Fail fast - Circuit breaker should return the fall back method" in {

    server.stubFor(
      post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
    )
    val app = builder.build()
    running(app) {
      val worker = app.injector.instanceOf[XrsExchangeRateRequestWorker]

      val workerResponse = worker.tap.pull().futureValue.value
      workerResponse.status shouldBe SERVICE_UNAVAILABLE
    }
  }

  "xrs exchange rate service call must not fail with empty exchange rate response" in {

    server.stubFor(
      post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))
        .willReturn(aResponse().withStatus(OK).withBody(mockedEmptyExchangeRatesJsonResponse))
    )
    val app = builder.build()
    running(app) {
      val worker = app.injector.instanceOf[XrsExchangeRateRequestWorker]

      val workerResponse = worker.tap.pull().futureValue.value
      workerResponse.status shouldBe OK
      workerResponse.body   shouldBe mockedEmptyExchangeRatesJsonResponse
    }
  }

  "xrs exchange rate service call must not fail with invalid Json response" in {

    val invalidJsonResponse = "This is not JSON"
    server.stubFor(
      post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))
        .willReturn(aResponse().withStatus(OK).withBody(invalidJsonResponse))
    )
    val app                 = builder.build()
    running(app) {
      val worker = app.injector.instanceOf[XrsExchangeRateRequestWorker]

      val workerResponse = worker.tap.pull().futureValue.value
      workerResponse.status shouldBe OK
      workerResponse.body   shouldBe invalidJsonResponse
    }

  }

  "xrs exchange rate service call must not fail with invalid XRS file format" in {

    val invalidJsonResponse = "{}"
    server.stubFor(
      post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))
        .willReturn(aResponse().withStatus(OK).withBody(invalidJsonResponse))
    )
    val app                 = builder.build()
    running(app) {
      val worker = app.injector.instanceOf[XrsExchangeRateRequestWorker]

      val workerResponse = worker.tap.pull().futureValue.value
      workerResponse.status shouldBe OK
      workerResponse.body   shouldBe invalidJsonResponse
    }

  }

  "areRatesForNextMonth is true if all validFrom dates start next month" in {
    val exchangeRateData: ExchangeRateData = ExchangeRateData(
      "",
      "",
      Seq(
        ExchangeRate(nextMonth, inTwoMonths, "UDS", BigDecimal(0.75d), "US Dollars"),
        ExchangeRate(nextMonth, inTwoMonths, "EU", BigDecimal(0.95d), "Euro")
      )
    )

    val rateRequest = new XrsExchangeRateRequest {}
    rateRequest.areRatesForNextMonth(exchangeRateData) shouldBe true
  }

  "areRatesForNextMonth is false if validFrom dates have a mixture that start next month and valid from this month" in {
    val exchangeRateData: ExchangeRateData = ExchangeRateData(
      "",
      "",
      Seq(
        ExchangeRate(nextMonth, inTwoMonths, "UDS", BigDecimal(0.75d), "US Dollars"),
        ExchangeRate(thisMonth, nextMonth, "EU", BigDecimal(0.95d), "Euro")
      )
    )

    val rateRequest = new XrsExchangeRateRequest {}
    rateRequest.areRatesForNextMonth(exchangeRateData) shouldBe false
  }

  "areRatesForNextMonth is false if all validFrom dates start this month" in {
    val exchangeRateData: ExchangeRateData = ExchangeRateData(
      "",
      "",
      Seq(
        ExchangeRate(thisMonth, nextMonth, "UDS", BigDecimal(0.75d), "US Dollars"),
        ExchangeRate(thisMonth, nextMonth, "EU", BigDecimal(0.95d), "Euro")
      )
    )

    val rateRequest = new XrsExchangeRateRequest {}
    rateRequest.areRatesForNextMonth(exchangeRateData) shouldBe false
  }

  "areRatesForNextMonth is false if all validFrom dates are for last month" in {
    val exchangeRateData: ExchangeRateData = ExchangeRateData(
      "",
      "",
      Seq(
        ExchangeRate(lastMonth, lastMonth, "UDS", BigDecimal(0.75d), "US Dollars"),
        ExchangeRate(lastMonth, lastMonth, "EU", BigDecimal(0.95d), "Euro")
      )
    )

    val rateRequest = new XrsExchangeRateRequest {}
    rateRequest.areRatesForNextMonth(exchangeRateData) shouldBe false
  }

  "when data not present for next month return false" in {
    val rateRequest                = new XrsExchangeRateRequest {
      override private[workers] def now = LocalDate.of(year, month, dayOfMonth)
    }
    val mockExchangeRateRepository = mock(classOf[ExchangeRateRepository])
    when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0722"))
      .thenReturn(Future.successful(false))
    false shouldBe await(rateRequest.isNextMonthsFileIsReceived(mockExchangeRateRepository))
  }

  "when data is present for next month return true" in {
    val rateRequest                = new XrsExchangeRateRequest {
      override private[workers] def now = LocalDate.of(year, month, dayOfMonth)
    }
    val mockExchangeRateRepository = mock(classOf[ExchangeRateRepository])
    when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0722"))
      .thenReturn(Future.successful(true))
    true shouldBe await(rateRequest.isNextMonthsFileIsReceived(mockExchangeRateRepository))
  }

  "checkNextMonthsFileIsReceivedDaysBeforeEndOfMonth" should {
    "when within range to check if next months file has been received returns true" in {
      val rateRequest = new XrsExchangeRateRequest {
        override private[workers] def now = LocalDate.of(year, month, dayOfMonth)
      }
      rateRequest.checkNextMonthsFileIsReceivedDaysBeforeEndOfMonth shouldBe true
    }

    "when not within range to check if next months file has been received return false" in {
      val rateRequest = new XrsExchangeRateRequest {
        override private[workers] def now = LocalDate.of(year, month, dayOfMonth - 1)
      }
      rateRequest.checkNextMonthsFileIsReceivedDaysBeforeEndOfMonth shouldBe false
    }
  }

  "verifyExchangeDataIsNotEmpty" should {

    "is true when data is not empty" in {
      val exchangeRateData: ExchangeRateData = ExchangeRateData(
        "",
        "",
        Seq(
          ExchangeRate(nextMonth, inTwoMonths, "UDS", BigDecimal(0.75d), "US Dollars"),
          ExchangeRate(nextMonth, inTwoMonths, "EU", BigDecimal(0.95d), "Euro")
        )
      )
      val rateRequest                        = new XrsExchangeRateRequest {}
      rateRequest.verifyExchangeDataIsNotEmpty(Try(exchangeRateData)) shouldBe true
    }

    "is false when data is empty" in {
      val exchangeRateData: ExchangeRateData = ExchangeRateData("", "", Seq())
      val rateRequest                        = new XrsExchangeRateRequest {}
      rateRequest.verifyExchangeDataIsNotEmpty(Try(exchangeRateData)) shouldBe false
    }

    "is false when unsuccessful" in {
      val rateRequest = new XrsExchangeRateRequest {}
      rateRequest.verifyExchangeDataIsNotEmpty(Failure(new RuntimeException("failed"))) shouldBe false
    }

  }

}
