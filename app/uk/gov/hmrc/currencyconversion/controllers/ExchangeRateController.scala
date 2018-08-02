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

package uk.gov.hmrc.currencyconversion.controllers

import java.time.LocalDate

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.currencyconversion.services.ExchangeRateService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

@Singleton()
class ExchangeRateController @Inject()(exchangeRatesService: ExchangeRateService) extends BaseController {

  //TODO: This now needs to accept a list of currencies and return the rates for each
  def getRatesByCurrencyCode(cc: String): Action[AnyContent] = Action.async { implicit request =>

    val rate = exchangeRatesService.getRate(LocalDate.now(), cc)

    val result = rate match {
      case Some(r) => Ok(r)
      case None => NotFound(s"No exchange rate found for $cc")
    }


    Future.successful(result)
  }
}