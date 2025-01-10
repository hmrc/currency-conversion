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

package uk.gov.hmrc.currencyconversion.controllers

import play.api.libs.json.Json
import play.api.mvc.*
import uk.gov.hmrc.currencyconversion.services.ExchangeRateService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class ExchangeRateController @Inject() (
  exchangeRatesService: ExchangeRateService,
  controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(controllerComponents) {

  def getRatesByCurrencyCode(cc: List[String], date: LocalDate): Action[AnyContent] = Action.async {

    exchangeRatesService.getRates(date, cc).map { exchangeRateResults =>
      val rates = exchangeRateResults.map(_.rate)
      Ok(Json.toJson(rates))
    }
  }

  def getCurrenciesByDate(date: LocalDate): Action[AnyContent] = Action.async {
    exchangeRatesService.getCurrencies(date).map(rates => Ok(Json.toJson(rates)))
  }
}
