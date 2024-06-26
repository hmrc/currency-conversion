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

package uk.gov.hmrc.currencyconversion.services

import java.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.test.Helpers._
import uk.gov.hmrc.currencyconversion.models._
import uk.gov.hmrc.currencyconversion.repositories.ConversionRatePeriodRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExchangeRateServiceSpec extends AnyWordSpecLike with Matchers {

  private val mockConversionRatePeriodRepository: ConversionRatePeriodRepository =
    Mockito.mock(classOf[ConversionRatePeriodRepository])

  private val exchangeRateService: ExchangeRateService = new ExchangeRateService(mockConversionRatePeriodRepository)

  private val currencies: Seq[Currency] = Seq(
    Currency(
      countryName = "India",
      currencyName = "Indian rupee",
      currencyCode = "INR"
    )
  )

  private val currencyPeriod: CurrencyPeriod = CurrencyPeriod(
    start = LocalDate.parse("2019-09-01"),
    end = LocalDate.parse("2019-09-30"),
    currencies = currencies
  )

  "ExchangeRateService" when {
    ".getCurrencies" should {
      "return currency period" in {
        when(mockConversionRatePeriodRepository.getCurrencyPeriod(any()))
          .thenReturn(Future.successful(Some(currencyPeriod)))

        await(exchangeRateService.getCurrencies(LocalDate.parse("2019-09-01"))) shouldBe currencyPeriod
      }

      "return None" in {
        when(mockConversionRatePeriodRepository.getCurrencyPeriod(any())).thenReturn(Future.successful(None))

        intercept[RuntimeException] {
          await(exchangeRateService.getCurrencies(LocalDate.parse("2019-09-01")))
        }.getMessage shouldBe "Exchange rate file is not available."
      }
    }
  }
}
