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

package uk.gov.hmrc.currencyconversion.connectors

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.test.Injecting
import uk.gov.hmrc.http.HeaderCarrier
import org.scalatest.matchers.must.Matchers
import org.scalatest.freespec.AnyFreeSpec
import uk.gov.hmrc.currencyconversion.utils.WireMockHelper

class HODConnectorSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with WireMockHelper
with ScalaFutures with IntegrationPatience with Injecting with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    SharedMetricRegistries.clear()
  }

  override lazy val app: Application = {
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.des.port"                    -> server.port(),
        "microservice.services.des.circuit-breaker.max-failures"  -> 1,
        "microservice.services.des.circuit-breaker.reset-timeout" -> "1 second"
      )
      .build()
  }

  private lazy val connector: HODConnector = inject[HODConnector]

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private def stubCall: MappingBuilder =
    post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))


  "hod connector" - {

    "must call the HOD when xrs worker thread is started" in {


      server.stubFor(
        stubCall
          .willReturn(aResponse().withStatus(OK))

      )
      connector.submit().futureValue.status mustBe OK
    }

    "must fall back to a 503 (SERVICE_UNAVAILABLE) when the downstream call errors" in {
      server.stubFor(
        stubCall
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
          .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE))
      )
      connector.submit().futureValue.status mustBe SERVICE_UNAVAILABLE
    }

    "must fail fast while the circuit breaker is open when Xrs call is triggered" in {
      server.stubFor(
        stubCall
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
          .willReturn(aResponse().withStatus(INSUFFICIENT_STORAGE))
      )
      connector.submit().futureValue.status mustBe SERVICE_UNAVAILABLE
    }
  }
}
