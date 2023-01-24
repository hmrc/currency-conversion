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

package uk.gov.hmrc.currencyconversion.models

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Json, OWrites, Reads, __}

import java.time.LocalDate

case class ExchangeRate(
  validFrom: LocalDate,
  validTo: LocalDate,
  currencyCode: String,
  exchangeRate: BigDecimal,
  currencyName: String
)

object ExchangeRate {

  implicit lazy val reads: Reads[ExchangeRate] = (
    (__ \ "validFrom").read[LocalDate] and
      (__ \ "validTo").read[LocalDate] and
      (__ \ "currencyCode").read[String] and
      (__ \ "exchangeRate").read[BigDecimal] and
      (__ \ "currencyName").read[String]
  )(ExchangeRate.apply _)

  implicit lazy val writes: OWrites[ExchangeRate] = Json.writes[ExchangeRate]
}
case class ExchangeRateData(
  timestamp: String,
  correlationId: String,
  exchangeData: Seq[ExchangeRate]
)

object ExchangeRateData {

  implicit lazy val reads: Reads[ExchangeRateData] = (
    (__ \ "timestamp").read[String] and
      (__ \ "correlationid").read[String] and
      (__ \ "exchangeRates").readWithDefault[Seq[ExchangeRate]](Seq.empty)
  )(ExchangeRateData.apply _)

  implicit lazy val writes: OWrites[ExchangeRateData] = Json.writes[ExchangeRateData]
}
