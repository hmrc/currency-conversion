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

package uk.gov.hmrc.currencyconversion.connectors

import org.apache.pekko.pattern.CircuitBreaker
import com.google.inject.Inject
import com.google.inject.name.Named
import play.api.Configuration
import play.api.http.Status.{OK, SERVICE_UNAVAILABLE}
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.currencyconversion.models.Service
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import java.util.UUID
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HODConnector @Inject() (
  http: HttpClient,
  config: Configuration,
  @Named("des") circuitBreaker: CircuitBreaker
)(implicit ec: ExecutionContext)
    extends HttpDate {

  private val bearerToken = config.get[String]("microservice.services.des.bearer-token")
  private val baseUrl     = config.get[Service]("microservice.services.des")
  private val xrsEndPoint = config.get[String]("microservice.services.des.endpoint")
  private val environment = config.get[String]("microservice.services.des.environment")

  private val CORRELATION_ID: String = "X-Correlation-ID"
  private val ENVIRONMENT: String    = "Environment"

  def submit(): Future[HttpResponse] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrier()
        .withExtraHeaders(
          HeaderNames.ACCEPT        -> ContentTypes.JSON,
          HeaderNames.CONTENT_TYPE  -> ContentTypes.JSON,
          HeaderNames.DATE          -> now,
          HeaderNames.AUTHORIZATION -> s"Bearer $bearerToken",
          CORRELATION_ID            -> UUID.randomUUID.toString,
          ENVIRONMENT               -> environment
        )

    def call(implicit hc: HeaderCarrier): Future[HttpResponse] =
      http.POST[JsValue, HttpResponse](s"$baseUrl$xrsEndPoint", Json.parse("""{}""")).map { response =>
        (response.status: @unchecked) match {
          case OK => HttpResponse(OK, response.body)
        }
      }

    circuitBreaker
      .withCircuitBreaker(call)
      .fallbackTo(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, s"Fall back response from $baseUrl$xrsEndPoint")))
  }
}
