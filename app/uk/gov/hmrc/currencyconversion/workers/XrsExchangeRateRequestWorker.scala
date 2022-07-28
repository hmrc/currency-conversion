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

package uk.gov.hmrc.currencyconversion.workers

import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.Status.SERVICE_UNAVAILABLE
import play.api.i18n.Lang.logger.logger
import play.api.libs.json.JsObject
import uk.gov.hmrc.currencyconversion.connectors.HODConnector
import uk.gov.hmrc.currencyconversion.models.ExchangeRateData
import uk.gov.hmrc.currencyconversion.repositories.ExchangeRateRepository
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx}
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}
import scala.util.control.Exception._
import scala.util.control.NonFatal

@Singleton
class XrsExchangeRateRequestWorker @Inject()(
                                              config: Configuration,
                                              hodConnector: HODConnector,
                                              writeExchangeRateRepository: ExchangeRateRepository,
                                              xrsDelayHelper: XrsDelayHelper
                                            )(implicit mat: Materializer, ec: ExecutionContext) {

  private val initialDelayFromConfig = config.get[String]("workers.xrs-exchange-rate.initial-delay").replace('.', ' ')
  private val parallelism = config.get[String]("workers.xrs-exchange-rate.parallelism").replace('.', ' ')
  private val initialDelayFromConfigFiniteDuration = config.get[FiniteDuration]("workers.xrs-exchange-rate.initial-delay")
  private val finiteInitialDelay = Duration(initialDelayFromConfig)
  private val initialDelay = Some(finiteInitialDelay).collect { case d: FiniteDuration => d }.getOrElse(initialDelayFromConfigFiniteDuration)
  private val scheduledTime: Boolean = config.get[Boolean]("workers.xrs-exchange-rate.scheduled-time")

  private val intervalFromConfig = config.get[String]("workers.xrs-exchange-rate.interval").replace('.', ' ')
  private val intervalFromConfigFiniteDuration = config.get[FiniteDuration]("workers.xrs-exchange-rate.interval")
  private val finiteInterval = Duration(intervalFromConfig)
  private val interval = Some(finiteInterval).collect { case d: FiniteDuration => d }.getOrElse(intervalFromConfigFiniteDuration)


  private val supervisionStrategy: Supervision.Decider = {
    case NonFatal(_) => Supervision.resume
    case _ => Supervision.stop
  }

  val tap: SinkQueueWithCancel[HttpResponse] = {
    Source.tick(xrsDelayHelper.calculateInitialDelay(scheduledTime, initialDelay), interval, Tick())
      .mapAsync(allCatch.opt(parallelism.toInt).getOrElse(1)) {
        _ =>
          hodConnector.submit().flatMap {
            case response: HttpResponse if is2xx(response.status) =>
              successfulResponse(response)
            case response: HttpResponse if is4xx(response.status) =>
              logger.error(s"XRS_BAD_REQUEST_FROM_EIS_ERROR  [XrsExchangeRateRequestWorker] call to DES (EIS) is failed. ${response.toString}")
              Future.successful(response)
            case _ => logger.error(s"XRS_BAD_REQUEST_FROM_EIS_ERROR [XrsExchangeRateRequestWorker] BAD Request is received from DES (EIS)")
              Future.successful(HttpResponse(SERVICE_UNAVAILABLE, "Service Unavailable"))
          }
      }
      .wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
      .run()
  }

  private def successfulResponse(response: HttpResponse) = {
    getResponseJson(response).map {
      exchangeRatesJson =>
        logExchangeDataSize(exchangeRatesJson)
        writeExchangeRateRepository.insertOrUpdate(exchangeRatesJson)
    }
    Future.successful(response)
  }

  private def getResponseJson(response: HttpResponse) = {
    Try {
      response.json.as[JsObject]
    } recoverWith {
      case e: Throwable =>
        logger.error("[XrsExchangeRateRequestWorker] [getResponseJson] Cannot convert response to JSON")
        Failure(e)
    }
  }

  private def logExchangeDataSize(exchangeRatesJson: JsObject): Unit = {
    Try {
      val tps = exchangeRatesJson.as[ExchangeRateData]
      if (tps.exchangeData.isEmpty) {
        logger.warn("[XrsExchangeRateRequestWorker] [logExchangeDataSize] Exchange Data size is 0")
      } else {
        logger.info(s"[XrsExchangeRateRequestWorker] [logExchangeDataSize] Exchange Data size is ${tps.exchangeData.size}")
      }
    } getOrElse {
      logger.error("[XrsExchangeRateRequestWorker] [logExchangeDataSize] Cannot convert response JSON to ExchangeRateData")
    }
  }
}

case class Tick()

