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

package uk.gov.hmrc.currencyconversion.workers

import com.google.inject.{Inject, Singleton}
import org.apache.pekko.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import org.apache.pekko.stream.{ActorAttributes, Materializer, Supervision}
import play.api.Configuration
import play.api.http.Status.SERVICE_UNAVAILABLE
import play.api.i18n.Lang.logger.logger
import play.api.libs.json.JsObject
import uk.gov.hmrc.currencyconversion.connectors.HODConnector
import uk.gov.hmrc.currencyconversion.models.ExchangeRateData
import uk.gov.hmrc.currencyconversion.repositories.ExchangeRateRepository
import uk.gov.hmrc.currencyconversion.utils.MongoIdHelper.currentFileName
import uk.gov.hmrc.http.HttpErrorFunctions.is2xx
import uk.gov.hmrc.http.HttpResponse

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.Exception._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

@Singleton
class XrsExchangeRateRequestWorker @Inject() (
  config: Configuration,
  hodConnector: HODConnector,
  writeExchangeRateRepository: ExchangeRateRepository,
  xrsDelayHelper: XrsDelayHelper
)(implicit mat: Materializer, ec: ExecutionContext)
    extends XrsExchangeRateRequest {

  private val initialDelayFromConfig               = config.get[String]("workers.xrs-exchange-rate.initial-delay").replace('.', ' ')
  private val parallelism                          = config.get[String]("workers.xrs-exchange-rate.parallelism").replace('.', ' ')
  private val initialDelayFromConfigFiniteDuration =
    config.get[FiniteDuration]("workers.xrs-exchange-rate.initial-delay")
  private val finiteInitialDelay                   = Duration(initialDelayFromConfig)
  private val initialDelay                         =
    Some(finiteInitialDelay).collect { case d: FiniteDuration => d }.getOrElse(initialDelayFromConfigFiniteDuration)
  private val scheduledTime: Boolean               = config.get[Boolean]("workers.xrs-exchange-rate.scheduled-time")

  private val intervalFromConfig                                          = config.get[String]("workers.xrs-exchange-rate.interval").replace('.', ' ')
  private val intervalFromConfigFiniteDuration                            = config.get[FiniteDuration]("workers.xrs-exchange-rate.interval")
  override protected val daysBeforeNextMonthToAlertForNextMonthsFile: Int =
    config.get[Int]("workers.xrs-exchange-rate.next-month-alert-days")
  private val finiteInterval                                              = Duration(intervalFromConfig)
  private val interval                                                    =
    Some(finiteInterval).collect { case d: FiniteDuration => d }.getOrElse(intervalFromConfigFiniteDuration)

  private val supervisionStrategy: Supervision.Decider                    = {
    case NonFatal(_) => Supervision.resume
    case _           => Supervision.stop
  }

  val tap: SinkQueueWithCancel[HttpResponse] =
    Source
      .tick(xrsDelayHelper.calculateInitialDelay(scheduledTime, initialDelay), interval, Tick())
      .mapAsync(allCatch.opt(parallelism.toInt).getOrElse(1)) { _ =>
        val response = hodConnector.submit().flatMap {
          case response: HttpResponse if is2xx(response.status) =>
            successfulResponse(response)
          case _                                                =>
            logger.error(
              s"[XrsExchangeRateRequestWorker][tap] XRS_BAD_REQUEST_FROM_EIS_ERROR BAD Request is received from DES (EIS)"
            )
            Future.successful(HttpResponse(SERVICE_UNAVAILABLE, "Service Unavailable"))
        }
        if (checkNextMonthsFileIsReceivedDaysBeforeEndOfMonth) {
          isNextMonthsFileIsReceived(writeExchangeRateRepository)
        }
        response
      }
      .wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
      .run()

  private def successfulResponse(response: HttpResponse) = {
    getResponseJson(response).map { exchangeRatesJson =>
      val triedData = Try(exchangeRatesJson.as[ExchangeRateData])
      if (verifyExchangeDataIsNotEmpty(triedData)) {
        writeExchangeRateRepository.insertOrUpdate(exchangeRatesJson, areRatesForNextMonth(triedData.get))
      }
    }
    Future.successful(response)
  }

  private def getResponseJson(response: HttpResponse) =
    Try {
      response.json.as[JsObject]
    } recoverWith { case e: Throwable =>
      logger.error("[XrsExchangeRateRequestWorker][getResponseJson] Cannot convert response to JSON")
      Failure(e)
    }

}

trait XrsExchangeRateRequest {

  private[workers] def now: LocalDate = LocalDate.now

  protected val daysBeforeNextMonthToAlertForNextMonthsFile: Int = 5

  private[workers] def checkNextMonthsFileIsReceivedDaysBeforeEndOfMonth =
    now.plusDays(daysBeforeNextMonthToAlertForNextMonthsFile).getMonthValue != now.getMonthValue

  private[workers] def areRatesForNextMonth(exchangeRateData: ExchangeRateData): Boolean = {

    val totalRates      = exchangeRateData.exchangeData.size
    val nextMonthsRates = exchangeRateData.exchangeData.count(ed => ed.validFrom.isAfter(now.`with`(lastDayOfMonth())))
    val expiredRates    = exchangeRateData.exchangeData.count(ed => ed.validTo.isBefore(now))

    logger.info(
      s"[XrsExchangeRateRequest][areRatesForNextMonth] " +
        s"total rates=$totalRates, next months rates=$nextMonthsRates, expired rates=$expiredRates"
    )

    if (nextMonthsRates > 0 && totalRates != nextMonthsRates) {
      logger.error(
        s"[XrsExchangeRateRequest][areRatesForNextMonth] XRS_FILE_HAS_MIXED_MONTHS_ERROR Exchange rates file has a mixture of months. " +
          s"Total rates=$totalRates, next months rates=$nextMonthsRates, expired rates=$expiredRates."
      )
    }

    if (totalRates == nextMonthsRates) {
      logger.error(
        "[XrsExchangeRateRequest][areRatesForNextMonth] XRS_FILE_DETECTED_FOR_NEXT_MONTH Inserting XRS file for next month"
      )
    }
    totalRates == nextMonthsRates
  }

  private[workers] def isNextMonthsFileIsReceived(
    writeExchangeRateRepository: ExchangeRateRepository
  )(implicit ec: ExecutionContext): Future[Boolean] = {

    val fileName  = currentFileName(now.plusMonths(1))
    val isPresent = writeExchangeRateRepository.isDataPresent(fileName)

    isPresent.map {
      case true  => logger.info(s"[XrsExchangeRateRequest][isNextMonthsFileIsReceived] $fileName exists")
      case false =>
        logger.error(
          s"[XrsExchangeRateRequest][isNextMonthsFileIsReceived] XRS_FILE_NEXT_MONTH_NOT_RECEIVED $fileName"
        )
    }

    isPresent
  }

  private[workers] def verifyExchangeDataIsNotEmpty(exchangeRateDataTry: Try[ExchangeRateData]): Boolean =
    exchangeRateDataTry match {
      case Success(exchangeRateData) =>
        if (exchangeRateData.exchangeData.isEmpty) {
          logger.error(
            "[XrsExchangeRateRequestWorker][verifyExchangeDataIsNotEmpty] XRS_EMPTY_RATES_FILE_ERROR Exchange Data size is 0"
          )
        } else {
          logger.info(
            s"[XrsExchangeRateRequestWorker][verifyExchangeDataIsNotEmpty] " +
              s"Exchange Data size is ${exchangeRateData.exchangeData.size} with timestamp ${exchangeRateData.timestamp}"
          )
        }
        exchangeRateData.exchangeData.nonEmpty
      case Failure(exception)        =>
        logger.error(
          "[XrsExchangeRateRequestWorker][verifyExchangeDataIsNotEmpty] XRS_RATES_FILE_INVALID_FORMAT " +
            "Cannot convert response JSON to ExchangeRateData",
          exception
        )
        false
    }
}

case class Tick()
