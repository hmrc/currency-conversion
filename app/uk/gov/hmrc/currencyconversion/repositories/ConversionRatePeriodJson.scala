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

package uk.gov.hmrc.currencyconversion.repositories

import cats.data.EitherT
import play.api.i18n.Lang.logger.logger
import play.api.libs.json.JsSuccess
import uk.gov.hmrc.currencyconversion.errors.{XrsFileErrors, XrsFileMappingError, XrsFileNotFoundError, XrsFileSearchMaxNumberOfTimesError}
import uk.gov.hmrc.currencyconversion.models._
import uk.gov.hmrc.currencyconversion.utils.MongoIdHelper.currentFileName

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConversionRatePeriodJson @Inject() (writeExchangeRateRepository: ExchangeRateRepository)(implicit
  ec: ExecutionContext
) extends ConversionRatePeriodRepository {

  def getExchangeRateObjectFile(currentDate: LocalDate): Future[Either[XrsFileErrors, ExchangeRateObject]] = {

    val targetFileName = currentFileName(currentDate)

    writeExchangeRateRepository.get(targetFileName).map {
      case None       =>
        logger.error(s"[ConversionRatePeriodJson][getExchangeRateObjectFile] Date: $currentDate, XrsFileNotFoundError")
        Left(XrsFileNotFoundError)
      case Some(file) =>
        logger.info(s"[ConversionRatePeriodJson][getExchangeRateObjectFile] File found for Date: $currentDate")
        Right(file)
    }
  }

  def getExchangeRatesData(currentDate: LocalDate): Future[Either[XrsFileErrors, ExchangeRateData]] =
    getExchangeRateObjectFile(currentDate).map { response =>
      response.map(_.exchangeRateData.validate[ExchangeRateData]) match {
        case Right(JsSuccess(exchangeRateData, _)) => Right(exchangeRateData)
        case _                                     =>
          logger.error(
            s"[ConversionRatePeriodJson][getExchangeRatesData] Exchange rate data mapping has failed. Possibly unable to find file or error in json"
          )
          Left(XrsFileMappingError)
      }
    }

  def getMinimumDecimalScale(rate: BigDecimal): BigDecimal =
    if (rate.scale < 2) rate.setScale(2) else rate

  def getExchangeRates(currentDate: LocalDate): Future[Either[XrsFileErrors, Map[String, Some[BigDecimal]]]] =
    getExchangeRatesData(currentDate).map { exchangeRateData =>
      exchangeRateData
        .map(_.exchangeData.flatMap { data =>
          Map(data.currencyCode -> Some(getMinimumDecimalScale(data.exchangeRate)))
        })
        .map(_.toMap)
    }

  def getCurrencies(currentDate: LocalDate): Future[Either[XrsFileErrors, Seq[Currency]]] =
    getExchangeRatesData(currentDate).map { exchangeRates =>
      exchangeRates.map(_.exchangeData.map(data => Currency("", data.currencyCode, data.currencyName)))
    }

  def getConversionRatePeriod(date: LocalDate): Future[Either[XrsFileErrors, ConversionRatePeriod]] = {
    for {
      exchangeRateData    <- EitherT[Future, XrsFileErrors, ExchangeRateData](getExchangeRatesData(date))
      exchangeRates       <- EitherT[Future, XrsFileErrors, Map[String, Option[BigDecimal]]](getExchangeRates(date))
      startDate: LocalDate = exchangeRateData.exchangeData.map(_.validFrom.withDayOfMonth(1)).head // start of month
      endDate: LocalDate   = startDate.withDayOfMonth(startDate.lengthOfMonth()) // end of month
    } yield ConversionRatePeriod(startDate, endDate, None, exchangeRates)
  }.value

  def fileLookBack[A](desiredFileDate: LocalDate, numberOfMonthsToLookBack: Int)(
    f: LocalDate => Future[Either[XrsFileErrors, A]]
  ): Future[Either[XrsFileErrors, A]] = {

    def loop(index: Int, limit: Int, currentDate: LocalDate): Future[Either[XrsFileErrors, A]] =
      f(currentDate).flatMap {
        case _ if index >= limit => Future(Left(XrsFileSearchMaxNumberOfTimesError))
        case Left(_)             =>
          loop(index + 1, limit, currentDate.minusMonths(1))
        case Right(a)            => Future(Right(a))
      }

    loop(0, numberOfMonthsToLookBack, desiredFileDate)
  }

  def getCurrencyPeriod(date: LocalDate): Future[Either[XrsFileErrors, CurrencyPeriod]] =
    getCurrencies(date).map { eitherCurrencies =>
      eitherCurrencies.map { currencies =>
        CurrencyPeriod(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()), currencies)
      }
    }
}
