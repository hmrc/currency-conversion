/*
 * Copyright 2021 HM Revenue & Customs
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


import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.currencyconversion.utils.WireMockHelper

class XrsExchangeRateRequestWorkerSpec extends FreeSpec with MustMatchers
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar with WireMockHelper with Eventually {

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "workers.xrs-exchange-rate.interval" -> "1 second",
      "microservice.services.des.port" -> server.port()
    )

  private val mockedJsonResponse =
    """{"timestamp":"2021-06-15T15:41:38Z",
      |"correlationid":"72a89d23-0fc6-4212-92fc-ea8b05139c76",
      |"exchangeRates":[{"validFrom":"2021-06-15","validTo":"2021-06-15","currencyCode":"ARS","exchangeRate":133.25,"currencyName":"Peso"}]}"""
      .stripMargin

  "must call the xrs exchange rate service and receive the response" in {

    server.stubFor(
      post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))
        .willReturn(aResponse().withStatus(OK).withBody(mockedJsonResponse))
    )
    val app = builder.build()
    running(app) {
      val worker = app.injector.instanceOf[XrsExchangeRateRequestWorker]

      val workerResponse = worker.tap.pull.futureValue.value
      workerResponse.status mustBe OK
      workerResponse.body mustBe mockedJsonResponse
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

      val workerResponse = worker.tap.pull.futureValue.value
      workerResponse.status mustBe SERVICE_UNAVAILABLE
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

      val workerResponse = worker.tap.pull.futureValue.value
      workerResponse.status mustBe SERVICE_UNAVAILABLE
    }
  }
}
