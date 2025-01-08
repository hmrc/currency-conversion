/*
 * Copyright 2024 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import uk.gov.hmrc.currencyconversion.utils.WireMockHelper

class HODConnectorSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with WireMockHelper
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach {

  override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.des.port"                          -> server.port(),
        "microservice.services.des.circuit-breaker.max-failures"  -> 1,
        "microservice.services.des.circuit-breaker.reset-timeout" -> "1 second"
      )
      .build()

  private lazy val connector: HODConnector = app.injector.instanceOf[HODConnector]

  private def stubCall: MappingBuilder =
    post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))

  "HODConnector" - {

    "must call the HOD when xrs worker thread is started" in {
      server.stubFor(
        stubCall
          .willReturn(aResponse().withStatus(OK))
      )
      connector.submit().futureValue.status mustBe OK
    }

    "must fall back to a 503 (SERVICE_UNAVAILABLE) when the circuit breaker handles" - {
      def test(status: Int): Unit =
        s"2xx statuses and above with an invalid status $status" in {
          server.stubFor(
            stubCall
              .willReturn(aResponse().withStatus(status))
          )

          connector.submit().futureValue.status mustBe SERVICE_UNAVAILABLE
        }

      val invalidStatusResponses: Seq[Int] = Status.getClass.getDeclaredFields.toSeq
        .map { field =>
          field.setAccessible(true)
          field.get(field.getName)
        }
        .flatMap {
          case fieldValue: java.lang.Integer
              if fieldValue != CONTINUE && fieldValue != SWITCHING_PROTOCOLS && fieldValue != OK =>
            Some(fieldValue.toInt)
          case _ => None
        }

      invalidStatusResponses.foreach(status => test(status))

      Seq(CONTINUE, SWITCHING_PROTOCOLS).foreach { status =>
        s"1xx statuses with an invalid status $status" in {
          server.stubFor(
            stubCall
              .willReturn(aResponse().withStatus(status))
          )

          connector.submit().futureValue.status mustBe SERVICE_UNAVAILABLE
        }
      }
    }
  }

}
