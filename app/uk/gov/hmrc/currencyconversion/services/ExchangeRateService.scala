/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.libs.json._
import uk.gov.hmrc.currencyconversion.models._
import uk.gov.hmrc.currencyconversion.repositories.ConversionRatePeriodRepository

class ExchangeRateService @Inject()(exchangeRateRepository: ConversionRatePeriodRepository)  {


  def getRates(date: LocalDate, currencyCodes: List[String]): List[ExchangeRateResult] = {

    val conversionRatePeriod = exchangeRateRepository.getConversionRatePeriod(date)

    currencyCodes.map { currencyCode =>
      conversionRatePeriod match {
        case Some(crp) =>
          crp.rates.get(currencyCode) match {
            case Some(rate) => ExchangeRateSuccessResult(Json.obj("startDate" -> crp.startDate, "endDate" -> crp.endDate, "currencyCode" -> currencyCode, "rate" -> rate.map(_.toString())))
            case None => ExchangeRateSuccessResult(Json.obj("startDate" -> crp.startDate, "endDate" -> crp.endDate, "currencyCode" -> currencyCode))
          }
        case None =>
          val fallbackCrp = exchangeRateRepository.getLatestConversionRatePeriod
          fallbackCrp.rates.get(currencyCode) match {
            case Some(rate) => ExchangeRateOldFileResult(Json.obj("startDate" -> fallbackCrp.startDate, "endDate" -> fallbackCrp.endDate, "currencyCode" -> currencyCode, "rate" -> rate.map(_.toString())))
            case None => ExchangeRateOldFileResult(Json.obj("startDate" -> fallbackCrp.startDate, "endDate" -> fallbackCrp.endDate, "currencyCode" -> currencyCode))
          }
      }
    }
  }
}
