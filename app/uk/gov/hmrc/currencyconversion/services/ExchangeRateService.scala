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

package uk.gov.hmrc.currencyconversion.services

import play.api.Configuration
import play.api.libs.json._
import uk.gov.hmrc.currencyconversion.models._
import uk.gov.hmrc.currencyconversion.repositories.ConversionRatePeriodRepository

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ExchangeRateService @Inject() (
  conversionRatePeriodRepository: ConversionRatePeriodRepository,
  config: Configuration
)(implicit
  ec: ExecutionContext
) {

  private val fallBackMonthLimit = config.get[Int]("fallback.months")

  private def jsonObjectCreator(
    startDate: LocalDate,
    endDate: LocalDate,
    currencyCode: String,
    rate: Option[BigDecimal]
  ) =
    if (rate.isDefined) {
      Json.obj(
        "startDate"    -> startDate,
        "endDate"      -> endDate,
        "currencyCode" -> currencyCode,
        "rate"         -> rate.map(_.toString())
      )
    } else {
      Json.obj(
        "startDate"    -> startDate,
        "endDate"      -> endDate,
        "currencyCode" -> currencyCode
      )
    }

  def getFallbackConversionRatePeriod(date: LocalDate, currencyCode: String): Future[ExchangeRateOldFileResult] =
    conversionRatePeriodRepository
      .fileLookBack(date, fallBackMonthLimit)(conversionRatePeriodRepository.getConversionRatePeriod)
      .map {
        case Right(fallbackConversionRatePeriod) =>
          fallbackConversionRatePeriod.rates.get(currencyCode) match {
            case Some(rate) =>
              ExchangeRateOldFileResult(
                jsonObjectCreator(
                  fallbackConversionRatePeriod.startDate,
                  fallbackConversionRatePeriod.endDate,
                  currencyCode,
                  rate
                )
              )
            case None       =>
              ExchangeRateOldFileResult(
                jsonObjectCreator(
                  fallbackConversionRatePeriod.startDate,
                  fallbackConversionRatePeriod.endDate,
                  currencyCode,
                  None
                )
              )
          }
        case Left(error)                         =>
          throw new Exception(s"[ExchangeRateService][lookBackAndGetFallbackData] Look back fail with error: $error")
      }

  def getConversionRateHelper(date: LocalDate, currencyCode: String): Future[ExchangeRateResult] = {
    val conversionRatePeriod = conversionRatePeriodRepository.getConversionRatePeriod(date)
    conversionRatePeriod.flatMap {
      case Right(conversionRatePeriod) =>
        conversionRatePeriod.rates.get(currencyCode) match {
          case Some(rate) =>
            Future(
              ExchangeRateSuccessResult(
                jsonObjectCreator(conversionRatePeriod.startDate, conversionRatePeriod.endDate, currencyCode, rate)
              )
            )
          case None       =>
            Future(
              ExchangeRateSuccessResult(
                jsonObjectCreator(conversionRatePeriod.startDate, conversionRatePeriod.endDate, currencyCode, None)
              )
            )
        }
      case Left(_)                     =>
        getFallbackConversionRatePeriod(date, currencyCode)
    }
  }

  def getRates(date: LocalDate, currencyCodes: List[String]): Future[List[ExchangeRateResult]] =
    Future.sequence {
      currencyCodes.map { currencyCode: String => getConversionRateHelper(date, currencyCode) }
    }

  def getCurrencies(date: LocalDate) =
    conversionRatePeriodRepository.fileLookBack(date, 6)(conversionRatePeriodRepository.getCurrencyPeriod)

}
