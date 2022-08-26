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

package uk.gov.hmrc.currencyconversion.services

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.lastDayOfMonth
import javax.inject.Inject
import play.api.libs.json._
import uk.gov.hmrc.currencyconversion.models._
import uk.gov.hmrc.currencyconversion.repositories.ConversionRatePeriodRepository

import scala.concurrent.{ExecutionContext, Future}

class ExchangeRateService @Inject() (exchangeRateRepository: ConversionRatePeriodRepository)(implicit
  ec: ExecutionContext
) {

  def getRates(date: LocalDate, currencyCodes: List[String]): Future[List[ExchangeRateResult]] = {
    val conversionRatePeriod = exchangeRateRepository.getConversionRatePeriod(date)
    Future.sequence {
      currencyCodes.map { currencyCode =>
        conversionRatePeriod.flatMap {
          case Some(crp) =>
            crp.rates.get(currencyCode) match {
              case Some(rate) =>
                Future.successful(
                  ExchangeRateSuccessResult(
                    Json.obj(
                      "startDate"    -> crp.startDate,
                      "endDate"      -> crp.endDate,
                      "currencyCode" -> currencyCode,
                      "rate"         -> rate.map(_.toString())
                    )
                  )
                )
              case None       =>
                Future.successful(
                  ExchangeRateSuccessResult(
                    Json.obj("startDate" -> crp.startDate, "endDate" -> crp.endDate, "currencyCode" -> currencyCode)
                  )
                )
            }
          case None      =>
            exchangeRateRepository.getLatestConversionRatePeriod(date.minusMonths(1)).map { fallbackCrp =>
              fallbackCrp.rates.get(currencyCode) match {
                case Some(rate) =>
                  ExchangeRateOldFileResult(
                    Json.obj(
                      "startDate"    -> fallbackCrp.startDate,
                      "endDate"      -> fallbackCrp.endDate,
                      "currencyCode" -> currencyCode,
                      "rate"         -> rate.map(_.toString())
                    )
                  )
                case None       =>
                  ExchangeRateOldFileResult(
                    Json.obj(
                      "startDate"    -> fallbackCrp.startDate,
                      "endDate"      -> fallbackCrp.endDate,
                      "currencyCode" -> currencyCode
                    )
                  )
              }
            }
        }
      }
    }
  }

  def getCurrencies(date: LocalDate): Future[Option[CurrencyPeriod]] =
    exchangeRateRepository.getCurrencyPeriod(date).flatMap {
      case Some(value) => Future.successful(Some(value))
      case None        =>
        val fallbackDate = date.minusMonths(1).`with`(lastDayOfMonth()) //lastDay of previous month
        exchangeRateRepository.getCurrencyPeriod(fallbackDate)
    }

}
