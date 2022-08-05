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

package uk.gov.hmrc.currencyconversion.repositories

import akka.stream.Materializer
import play.api.i18n.Lang.logger.logger
import play.api.libs.json.JsSuccess
import play.api.libs.json.OFormat.oFormatFromReadsAndOWrites
import uk.gov.hmrc.currencyconversion.models.{ConversionRatePeriod, Currency, CurrencyPeriod, ExchangeRateData}
import uk.gov.hmrc.currencyconversion.utils.MongoIdHelper.currentFileName

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps


class ConversionRatePeriodJson @Inject()(writeExchangeRateRepository: ExchangeRateRepository)
                                        (implicit ec: ExecutionContext, m: Materializer) extends ConversionRatePeriodRepository {

  def getExchangeRateFileName(date: LocalDate): Future[String] = {
    val targetFileName = currentFileName(date)

    writeExchangeRateRepository.isDataPresent(targetFileName).map {
      case true => targetFileName
      case _ => logger.info(s"$targetFileName is not present")
        "empty"
    }
  }

  def getExchangeRatesData(filePath: String): Future[ExchangeRateData] = {
    writeExchangeRateRepository.get(filePath)
      .map {
        case response if response.isEmpty =>
          logger.error(s"XRS_FILE_CANNOT_BE_READ_ERROR [ConversionRatePeriodJson] Exchange rate file is not able to read")
          throw new RuntimeException("Exchange rate data is not able to read.")
        case response =>
          response.get.exchangeRateData.validate[ExchangeRateData] match {
            case JsSuccess(seq, _) =>
              seq
            case _ => {
              logger.error(s"XRS_FILE_CANNOT_BE_READ_ERROR [ConversionRatePeriodJson] Exchange rate data mapping is failed")
              throw new RuntimeException("Exchange rate data mapping is failed")
            }
          }
      }
  }

  def getExchangeRates(filePath: String): Future[Map[String, Option[BigDecimal]]] = {

    def getMinimumDecimalScale(rate: BigDecimal): BigDecimal = {
      if (rate.scale < 2) rate.setScale(2) else rate
    }

    getExchangeRatesData(filePath).map { exchangeRates =>
      exchangeRates.exchangeData.flatMap { data =>
        Map(data.currencyCode -> Some(getMinimumDecimalScale(data.exchangeRate)))
      }.toMap
    }
  }

  def getCurrencies(filePath: String): Future[Seq[Currency]] = {
    getExchangeRatesData(filePath).map { exchangeRates =>
      exchangeRates.exchangeData map { data =>
        Currency("", data.currencyCode, data.currencyName)
      }
    }
  }

  def getConversionRatePeriod(date: LocalDate): Future[Option[ConversionRatePeriod]] = {
    getExchangeRateFileName(date).flatMap { fileName =>

      if (fileName.equals("empty")) {
        Future.successful(None)
      } else {
        getExchangeRates(fileName).map { rates =>
          Some(ConversionRatePeriod(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()), None, rates))
        }
      }
    }
  }

  def getLatestConversionRatePeriod(date: LocalDate): Future[ConversionRatePeriod] = {
    getExchangeRateFileName(date).flatMap { fileName =>
      if (fileName.equals("empty")) {
        logger.error(s"XRS_FILE_NOT_AVAILABLE_ERROR [ConversionRatePeriodJson] Exchange rate file is not available.")
        Future.failed(new RuntimeException("Exchange rate file is not able to read."))
      } else {
        getExchangeRates(fileName).map { rates =>
          ConversionRatePeriod(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()), None, rates)
        }
      }
    }
  }

  def getCurrencyPeriod(date: LocalDate): Future[Option[CurrencyPeriod]] = {
    getExchangeRateFileName(date).flatMap { fileName =>
      getCurrencies(fileName).map { currencies =>
        Some(CurrencyPeriod(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()), currencies))
      }
    }
  }
}
