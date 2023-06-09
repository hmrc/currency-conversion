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

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.currencyconversion.errors._
import uk.gov.hmrc.currencyconversion.models._
import uk.gov.hmrc.currencyconversion.repositories.ConversionRatePeriodRepository

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExchangeRateServiceSpec extends AnyWordSpecLike with Matchers with MockitoSugar {

  private val mockConversionRatePeriodRepository: ConversionRatePeriodRepository = mock[ConversionRatePeriodRepository]
  private val mockConfiguration: Configuration                                   = mock[Configuration]

  private val exchangeRateService: ExchangeRateService =
    new ExchangeRateService(mockConversionRatePeriodRepository, mockConfiguration)

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

    ".getFallbackConversionRatePeriod()" when {

      "the file date is the same as current month" should {

        "return ExchangeRateSuccessResult with the correct json" in {

          when(mockConversionRatePeriodRepository.fileLookBack[ConversionRatePeriod](any(), any())(any()))
            .thenReturn(
              Future(
                Right(
                  ConversionRatePeriod(
                    startDate = LocalDate.parse("2023-09-01"),
                    endDate = LocalDate.parse("2023-09-30"),
                    currencyCode = Some("INR"),
                    rates = Map("INR" -> Some(1.213))
                  )
                )
              )
            )

          val expectedJson =
            Json.obj(
              "startDate"    -> "2023-09-01",
              "endDate"      -> "2023-09-30",
              "currencyCode" -> "INR",
              "rate"         -> "1.213"
            )

          val actual   = await(exchangeRateService.getFallbackConversionRatePeriod(LocalDate.parse("2023-09-01"), "INR"))
          val expected = ExchangeRateOldFileResult(expectedJson)

          actual shouldBe expected
        }
      }

      "the file date is the same as current month but with no rate" should {

        "return ExchangeRateSuccessResult with the correct json" in {

          when(mockConversionRatePeriodRepository.fileLookBack[ConversionRatePeriod](any(), any())(any()))
            .thenReturn(
              Future(
                Right(
                  ConversionRatePeriod(
                    startDate = LocalDate.parse("2023-09-01"),
                    endDate = LocalDate.parse("2023-09-30"),
                    currencyCode = Some("INR"),
                    rates = Map()
                  )
                )
              )
            )

          val expectedJson =
            Json.obj(
              "startDate"    -> "2023-09-01",
              "endDate"      -> "2023-09-30",
              "currencyCode" -> "INR"
            )

          val actual   = await(exchangeRateService.getFallbackConversionRatePeriod(LocalDate.parse("2023-09-01"), "INR"))
          val expected = ExchangeRateOldFileResult(expectedJson)

          actual shouldBe expected
        }
      }

      "the file is within the look back period" should {

        "return ExchangeRateOldFileResult with the correct json" in {

          when(mockConversionRatePeriodRepository.fileLookBack[ConversionRatePeriod](any(), any())(any()))
            .thenReturn(
              Future(
                Right(
                  ConversionRatePeriod(
                    startDate = LocalDate.parse("2023-07-01"),
                    endDate = LocalDate.parse("2023-07-31"),
                    currencyCode = Some("INR"),
                    rates = Map("INR" -> Some(1.213))
                  )
                )
              )
            )

          val expectedJson =
            Json.obj(
              "startDate"    -> "2023-07-01",
              "endDate"      -> "2023-07-31",
              "currencyCode" -> "INR",
              "rate"         -> "1.213"
            )

          val actual   = await(exchangeRateService.getFallbackConversionRatePeriod(LocalDate.parse("2019-09-01"), "INR"))
          val expected = ExchangeRateOldFileResult(expectedJson)

          actual shouldBe expected
        }
      }

      "the file is too old so is NOT within the look back period" should {

        "throw an exception" in {

          when(mockConversionRatePeriodRepository.fileLookBack[ConversionRatePeriod](any(), any())(any()))
            .thenReturn(Future(Left(XrsFileSearchMaxNumberOfTimesError)))

          intercept[Exception] {
            await(exchangeRateService.getFallbackConversionRatePeriod(LocalDate.parse("2019-09-01"), "INR"))
          }.getMessage shouldBe
            "[ExchangeRateService][lookBackAndGetFallbackData] Look back fail with error: XrsFileSearchMaxNumberOfTimesError"
        }
      }
    }

    ".getCurrencies" should {

      "return currency period" in {

        when(mockConversionRatePeriodRepository.getCurrencyPeriod(any()))
          .thenReturn(Future(Right(currencyPeriod)))

        when(mockConversionRatePeriodRepository.fileLookBack[CurrencyPeriod](any(), any())(any()))
          .thenReturn(Future(Right(currencyPeriod)))

        await(exchangeRateService.getCurrencies(LocalDate.parse("2019-09-01"))) shouldBe Right(currencyPeriod)
      }

      "return None" in {

        when(mockConversionRatePeriodRepository.getCurrencyPeriod(any()))
          .thenReturn(Future(Left(XrsFileMappingError)))

        when(mockConversionRatePeriodRepository.fileLookBack[CurrencyPeriod](any(), any())(any()))
          .thenReturn(Future(Left(XrsFileMappingError)))

        await(exchangeRateService.getCurrencies(LocalDate.parse("2019-09-01"))) shouldBe Left(XrsFileMappingError)
      }
    }
  }
}
