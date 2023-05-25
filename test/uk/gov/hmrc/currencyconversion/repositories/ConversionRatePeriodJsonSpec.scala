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

package uk.gov.hmrc.currencyconversion.repositories

import java.time.LocalDate

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.currencyconversion.models._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConversionRatePeriodJsonSpec extends AnyWordSpecLike with Matchers with MockitoSugar {

  private val mockExchangeRateRepository: ExchangeRateRepository = mock[ExchangeRateRepository]

  private val conversionRatePeriodJson: ConversionRatePeriodJson = new ConversionRatePeriodJson(
    mockExchangeRateRepository
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

    ".getLatestConversionRatePeriod" should {
      "throw RuntimeException" when {
        "no exchange rate data is present" in {
          when(mockExchangeRateRepository.isDataPresent(any())).thenReturn(Future.successful(false))

          intercept[RuntimeException] {
            await(conversionRatePeriodJson.getLatestConversionRatePeriod(LocalDate.parse("2019-09-01")))
          }.getMessage shouldBe "Exchange rate file is not able to read."
        }
      }
    }
  }
}
