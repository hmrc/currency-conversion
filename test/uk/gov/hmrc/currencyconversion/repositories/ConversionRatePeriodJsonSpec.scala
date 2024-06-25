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

package uk.gov.hmrc.currencyconversion.repositories

import java.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.currencyconversion.models._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConversionRatePeriodJsonSpec extends AnyWordSpecLike with Matchers {

  private val mockExchangeRateRepository: ExchangeRateRepository = Mockito.mock(classOf[ExchangeRateRepository])
  private val mockConfiguration: Configuration                   = Configuration("fallback.months" -> 6)

  private val conversionRatePeriodJson: ConversionRatePeriodJson = new ConversionRatePeriodJson(
    mockExchangeRateRepository,
    mockConfiguration
  )

  private val exchangeRateData: ExchangeRateData = ExchangeRateData(
    timestamp = "2019-06-28T13:17:21Z",
    correlationId = "c4a81105-9417-4080-9cd2-c4469efc965c",
    exchangeData = Seq(
      ExchangeRate(
        validFrom = LocalDate.parse("2019-09-01"),
        validTo = LocalDate.parse("2019-09-30"),
        currencyCode = "INR",
        exchangeRate = 1.213,
        currencyName = "Indian rupee"
      )
    )
  )

  private val exchangeRateDataJson: JsObject = Json
    .parse(
      """
      |{
      |    "timestamp": "2019-06-28T13:17:21Z",
      |    "correlationid": "c4a81105-9417-4080-9cd2-c4469efc965c",
      |    "exchangeRates": [
      |        {
      |            "validFrom": "2019-09-01",
      |            "validTo": "2019-09-30",
      |            "currencyCode": "INR",
      |            "exchangeRate": 1.213,
      |            "currencyName": "Indian rupee"
      |        }
      |    ]
      |}
    """.stripMargin
    )
    .as[JsObject]

  "ConversionRatePeriodJson" when {
    ".getExchangeRatesData" should {
      "return exchange rate data" in {
        when(mockExchangeRateRepository.get(any())).thenReturn(
          Future.successful(
            Some(
              ExchangeRateObject(
                fileName = "exrates-monthly-0919",
                exchangeRateData = exchangeRateDataJson
              )
            )
          )
        )

        val result: ExchangeRateData = await(conversionRatePeriodJson.getExchangeRatesData("exrates-monthly-1019"))

        result shouldBe exchangeRateData
      }

      "throw RuntimeException" when {
        "no exchange rate data is present" in {
          when(mockExchangeRateRepository.get(any())).thenReturn(Future.successful(None))

          intercept[RuntimeException] {
            await(conversionRatePeriodJson.getExchangeRatesData("exrates-monthly-1019"))
          }.getMessage shouldBe "Exchange rate data is not able to read."
        }

        "the exchange rate data present does not match the model" in {
          when(mockExchangeRateRepository.get(any())).thenReturn(
            Future.successful(
              Some(
                ExchangeRateObject(
                  fileName = "exrates-monthly-0919",
                  exchangeRateData = JsObject.empty
                )
              )
            )
          )

          intercept[RuntimeException] {
            await(conversionRatePeriodJson.getExchangeRatesData("exrates-monthly-1019"))
          }.getMessage shouldBe "Exchange rate data mapping is failed"
        }
      }
    }

    ".getConversionRatePeriod" should {
      "return None" when {
        "no exchange rate data is present" in {
          when(mockExchangeRateRepository.isDataPresent(any())).thenReturn(Future.successful(false))

          val result = await(conversionRatePeriodJson.getConversionRatePeriod(LocalDate.parse("2019-08-01")))
          result shouldBe None
        }
      }
    }

    ".getExchangeRateFileName" should {
      "return empty" when {
        "no exchange rate data is present" in {
          when(mockExchangeRateRepository.isDataPresent(any())).thenReturn(Future.successful(false))

          val result = await(conversionRatePeriodJson.getExchangeRateFileName(LocalDate.parse("2019-08-01")))
          result shouldBe "empty"
        }
      }

      "return a valid file" when {
        "there is an exchange rate file is present for the given date" in {
          when(mockExchangeRateRepository.isDataPresent(any())).thenReturn(Future.successful(true))

          val result = await(conversionRatePeriodJson.getExchangeRateFileName(LocalDate.parse("2019-08-01")))
          result shouldBe "exrates-monthly-0819"
        }
      }

      "return a valid file" when {
        "there is an exchange rate data is not present for given month but present 6 months before the requested date" in {
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0819")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0719")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0619")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0519")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0419")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0319")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0219")).thenReturn(Future.successful(true))

          val result = await(conversionRatePeriodJson.getExchangeRateFileName(LocalDate.parse("2019-08-01")))
          result shouldBe "exrates-monthly-0219"
        }
      }

      "return a empty" when {
        "there is an exchange rate data neither present for the given month nor in the last 6 months to the requested date" in {
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0819")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0719")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0619")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0519")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0419")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0319")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0219")).thenReturn(Future.successful(false))
          when(mockExchangeRateRepository.isDataPresent("exrates-monthly-0119")).thenReturn(Future.successful(true))

          val result = await(conversionRatePeriodJson.getExchangeRateFileName(LocalDate.parse("2019-08-01")))
          result shouldBe "empty"
        }
      }

    }
  }
}
