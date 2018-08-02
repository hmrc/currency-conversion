/*
 * Copyright 2018 HM Revenue & Customs
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

import java.io.InputStream
import java.time.LocalDate

import javax.inject.Inject
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.currencyconversion.repositories.ExchangeRateRepository

import scala.xml.{Elem, XML}


class ExchangeRateService @Inject() (exchangeRateRepository: ExchangeRateRepository) {


  def getRate(date: LocalDate, currencyCode: String): Option[JsObject] = {

    for {
      crp <- exchangeRateRepository.getRecentConversionRatePeriod(date)
      rate <- crp.rates.get(currencyCode)
      // TODO: we may no longer need the start and end date
    } yield Json.obj("startDate" -> crp.startDate, "endDate" -> crp.endDate, "rate" -> rate)
  }
}
