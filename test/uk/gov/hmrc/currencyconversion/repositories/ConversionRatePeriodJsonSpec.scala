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

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.currencyconversion.errors._
import uk.gov.hmrc.currencyconversion.models._
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConversionRatePeriodJsonSpec
    extends AnyWordSpecLike
    with Matchers
    with MockitoSugar
    with DefaultPlayMongoRepositorySupport[ExchangeRateObject] {

  val emptyExchangeRateDataModel =
    ExchangeRateObject(
      fileName = "exrates-monthly-0923",
      exchangeRateData = JsObject.empty
    ).exchangeRateData

  val feb2023 =
    ExchangeRateObject(
      fileName = "exrates-monthly-0223",
      exchangeRateData = exchangeRateDataJson("exrates-monthly-0223", "2023-02-01", "2023-02-28")
    )

  val july2023 =
    ExchangeRateObject(
      fileName = "exrates-monthly-0723",
      exchangeRateData = exchangeRateDataJson("exrates-monthly-0723", "2023-07-01", "2023-07-30")
    )

  val aug2023 =
    ExchangeRateObject(
      fileName = "exrates-monthly-0823",
      exchangeRateData = exchangeRateDataJson("exrates-monthly-0823", "2023-08-01", "2023-08-30")
    ).exchangeRateData

  val sept2023 =
    ExchangeRateObject(
      fileName = "exrates-monthly-0923",
      exchangeRateData = exchangeRateDataJson("exrates-monthly-0923", "2023-09-01", "2023-09-30")
    ).exchangeRateData

  private val mockExchangeRateRepository: ExchangeRateRepository = mock[ExchangeRateRepository]

  private val mockConfiguration: Configuration = mock[Configuration]

  //  private val fallBackMonthLimit = mockConfiguration[Int]("fallback.months")
  private val fallBackMonthLimit = 6

  override def repository: DefaultExchangeRateRepository = new DefaultExchangeRateRepository(mongoComponent)

  private val conversionRatePeriodJson: ConversionRatePeriodJson =
    new ConversionRatePeriodJson(mockExchangeRateRepository)

  val realConversionRatePeriodJson: ConversionRatePeriodJson =
    new ConversionRatePeriodJson(repository)

  private val exchangeRateData: ExchangeRateData =
    ExchangeRateData(
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

  override def beforeEach(): Unit =
    prepareDatabase()

  def exchangeRateDataJson(id: String, startDate: String, endDate: String): JsObject = Json
    .parse(
      s"""
         |{
         |    "_id" : "$id",
         |    "timestamp": "2019-06-28T13:17:21Z",
         |    "correlationid": "c4a81105-9417-4080-9cd2-c4469efc965c",
         |    "exchangeRates": [
         |        {
         |            "validFrom": "$startDate",
         |            "validTo": "$endDate",
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
                exchangeRateData = exchangeRateDataJson("exrates-monthly-0919", "2019-09-01", "2019-09-30")
              )
            )
          )
        )

        val actual   = await(conversionRatePeriodJson.getExchangeRatesData(LocalDate.parse("2019-10-01")))
        val expected = Right(exchangeRateData)

        actual shouldBe expected
      }

      "throw RuntimeException" when {

        "no exchange rate data is present" in {

          when(mockExchangeRateRepository.get(any())).thenReturn(Future.successful(None))

          val actual   = await(conversionRatePeriodJson.getExchangeRatesData(LocalDate.parse("2019-09-01")))
          val expected = Left(XrsFileMappingError)

          actual shouldBe expected
        }

        "the exchange rate data present does not match the model" in {

          when(mockExchangeRateRepository.get(any())).thenReturn(
            Future(
              Some(
                ExchangeRateObject(fileName = "exrates-monthly-0919", exchangeRateData = JsObject.empty)
              )
            )
          )

          val actual   = await(conversionRatePeriodJson.getExchangeRatesData(LocalDate.parse("2019-10-01")))
          val expected = Left(XrsFileMappingError)

          actual shouldBe expected
        }
      }
    }

    ".getLatestConversionRatePeriod" should {

      "throw RuntimeException" when {

        "no exchange rate data is present" in {

          when(mockExchangeRateRepository.isDataPresent(any())).thenReturn(Future.successful(false))

          val actual   = await(conversionRatePeriodJson.getExchangeRatesData(LocalDate.parse("2019-09-01")))
          val expected = Left(XrsFileMappingError)

          actual shouldBe expected
        }
      }
    }

    ".fileLookBack()" when {

      "given a desired date, with a file with a date within the lookback period, SUCCESSFULLY retrieve the fallback file" should {

        "return a Right(ConversionRatePeriod) exchange rate data for July 2023, for the desired date of September 2023" in {

          repository.testOnlyInsert(july2023.exchangeRateData, "exrates-monthly-0723")

          val actual =
            await(
              realConversionRatePeriodJson.fileLookBack[ConversionRatePeriod](
                desiredFileDate = LocalDate.parse("2023-09-01"),
                fallBackMonthLimit
              )(realConversionRatePeriodJson.getConversionRatePeriod)
            )

          val expected =
            Right(
              ConversionRatePeriod(
                startDate = LocalDate.parse("2023-07-01"),
                endDate = LocalDate.parse("2023-07-31"),
                currencyCode = None,
                rates = Map("INR" -> Some(1.213))
              )
            )

          actual shouldBe expected
        }
      }

      "given a date outside the fallback range, FAILS to retrieves any file" should {

        "return a Left(XrsFileSearchMaxNumberOfTimesError) for data outside the fallback date range" in {

          repository.testOnlyInsert(feb2023.exchangeRateData, "exrates-monthly-0223")

          val actual =
            await(
              realConversionRatePeriodJson.fileLookBack[ConversionRatePeriod](
                desiredFileDate = LocalDate.parse("2023-09-01"),
                fallBackMonthLimit
              )(realConversionRatePeriodJson.getConversionRatePeriod)
            )

          val expected = Left(XrsFileSearchMaxNumberOfTimesError)
          actual shouldBe expected
        }
      }
    }
  }
}
