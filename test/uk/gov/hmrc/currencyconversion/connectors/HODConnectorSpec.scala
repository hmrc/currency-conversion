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

package uk.gov.hmrc.currencyconversion.connectors

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.apache.pekko.pattern.CircuitBreaker
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerTest
import play.api.Application
import play.api.http.{ContentTypes, HeaderNames, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import uk.gov.hmrc.currencyconversion.utils.WireMockHelper
import org.apache.pekko.actor.ActorSystem

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

class HODConnectorSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneServerPerTest
    with WireMockHelper
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(30, Seconds), interval = Span(100, Millis))

  override def beforeEach(): Unit = {
    super.beforeEach()
    server.resetAll()
  }

  private def healthyCircuitBreaker(system: ActorSystem): CircuitBreaker = {
    implicit val ec: ExecutionContext = system.dispatcher

    new CircuitBreaker(
      system.scheduler,
      maxFailures = 2,
      callTimeout = FiniteDuration(2, TimeUnit.SECONDS),
      resetTimeout = FiniteDuration(1, TimeUnit.SECONDS)
    )
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides
      // Create a test ActorSystem and pass it to the circuit breaker
      {
        val system = ActorSystem("test-system")
        bind[CircuitBreaker].qualifiedWith("des").toInstance(healthyCircuitBreaker(system))
      }
      .configure(
        "microservice.services.des.port"         -> server.port(),
        "microservice.services.des.bearer-token" -> "test-bearer-token",
        "microservice.services.des.environment"  -> "test-environment",
        "microservice.services.des.endpoint"     -> "/passengers/exchangerequest/xrs/getexchangerate/v1"
      )
      .build()

  private def stubCall: MappingBuilder =
    post(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))

  private def stubWithDelay(status: Int, delayMs: Int = 3000): Unit =
    server.stubFor(
      stubCall
        .willReturn(
          aResponse()
            .withStatus(status)
            .withFixedDelay(delayMs)
        )
    )

  "HODConnector" - {

    "Header Verification" - {
      "must send the correct headers to the HOD" in {
        val connector           = app.injector.instanceOf[HODConnector]
        val expectedBearerToken = "test-bearer-token"
        val expectedEnvironment = "test-environment"
        val datePattern         = "[A-Za-z]{3}, \\d{2} [A-Za-z]{3} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT"
        val uuidPattern         = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

        server.stubFor(
          stubCall
            .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(s"Bearer $expectedBearerToken"))
            .withHeader("Environment", equalTo(expectedEnvironment))
            .withHeader(HeaderNames.DATE, matching(datePattern))
            .withHeader("X-Correlation-ID", matching(uuidPattern))
            .willReturn(aResponse().withStatus(OK))
        )

        val result = connector.submit().futureValue

        result.status mustBe OK

        server.verify(
          postRequestedFor(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))
            .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(s"Bearer $expectedBearerToken"))
            .withHeader("Environment", equalTo(expectedEnvironment))
            .withHeader(HeaderNames.DATE, matching(datePattern))
            .withHeader("X-Correlation-ID", matching(uuidPattern))
        )
      }

      "must return SERVICE_UNAVAILABLE (503) when the downstream service rejects the authorization token (returns 401)" in {
        val connector           = app.injector.instanceOf[HODConnector]
        val expectedBearerToken = "test-bearer-token"

        server.stubFor(
          stubCall
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(s"Bearer $expectedBearerToken"))
            .willReturn(
              aResponse()
                .withStatus(UNAUTHORIZED)
                .withBody("Invalid authentication credentials")
            )
        )

        val result = connector.submit().futureValue
        result.status mustBe SERVICE_UNAVAILABLE
        result.body must include(s"Fall back response from")
        server.verify(
          postRequestedFor(urlEqualTo("/passengers/exchangerequest/xrs/getexchangerate/v1"))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(s"Bearer $expectedBearerToken"))
        )
      }
    }

    "must call the HOD when xrs worker thread is started" in {
      val connector = app.injector.instanceOf[HODConnector]
      server.stubFor(
        stubCall
          .willReturn(aResponse().withStatus(OK))
      )
      connector.submit().futureValue.status mustBe OK
    }

    "must fall back to a 503 (SERVICE_UNAVAILABLE) when the circuit breaker handles" - {
      "non-OK responses" in {
        val connector = app.injector.instanceOf[HODConnector]
        stubWithDelay(INTERNAL_SERVER_ERROR)

        val result1 = connector.submit().futureValue
        result1.status mustBe SERVICE_UNAVAILABLE

        val result2 = connector.submit().futureValue
        result2.status mustBe SERVICE_UNAVAILABLE
      }

      "slow responses" in {
        val connector = app.injector.instanceOf[HODConnector]

        stubWithDelay(OK, 5000)

        val result = connector.submit().futureValue
        result.status mustBe SERVICE_UNAVAILABLE
      }
    }
  }
}
