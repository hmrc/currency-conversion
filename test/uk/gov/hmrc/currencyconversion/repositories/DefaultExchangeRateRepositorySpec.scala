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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.currencyconversion.models._
import uk.gov.hmrc.currencyconversion.utils.MongoIdHelper.currentFileName
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global

class DefaultExchangeRateRepositorySpec
    extends AnyWordSpecLike
    with Matchers
    with DefaultPlayMongoRepositorySupport[ExchangeRateObject] {

  override def repository: DefaultExchangeRateRepository = new DefaultExchangeRateRepository(mongoComponent)

  override def afterEach(): Unit = {
    super.afterEach()
    prepareDatabase()
  }

  private val date: LocalDate = LocalDate.now()

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
      |            "currencyName": "India"
      |        }
      |    ]
      |}
    """.stripMargin
    )
    .as[JsObject]

  private val updatedExchangeRateDataJson: JsObject = Json
    .parse(
      """
      |{
      |    "timestamp": "2020-06-28T13:17:21Z",
      |    "correlationid": "c4a81105-9417-4080-9cd2-c4469efc965c",
      |    "exchangeRates": [
      |        {
      |            "validFrom": "2020-09-01",
      |            "validTo": "2020-09-30",
      |            "currencyCode": "INR",
      |            "exchangeRate": 1.213,
      |            "currencyName": "India"
      |        }
      |    ]
      |}
    """.stripMargin
    )
    .as[JsObject]

  private val exchangeRateObject: ExchangeRateObject = ExchangeRateObject(
    fileName = currentFileName(date),
    exchangeRateData = exchangeRateDataJson
  )

  private trait Setup {
    await(insert(exchangeRateObject))
    await(insert(exchangeRateObject.copy(fileName = currentFileName(date.plusMonths(1)))))
  }

  "DefaultExchangeRateRepository" when {
    ".insert" should {
      "insert exchange rate data" when {
        "current month" in {
          repository.insert(exchangeRateData = exchangeRateDataJson) shouldBe ()
        }

        "next month" in {
          repository.insert(exchangeRateData = exchangeRateDataJson, forNextMonth = true) shouldBe ()
        }
      }
    }

    ".isDataPresent" should {
      "return false" when {
        "checking if exchange rate data exists in the next two months" in new Setup {
          await(repository.isDataPresent(currentFileName(date.plusMonths(2)))) shouldBe false
        }
      }

      "return true" when {
        "checking if exchange rate data exists in the current month" in new Setup {
          await(repository.isDataPresent(currentFileName(date))) shouldBe true
        }

        "checking if exchange rate data exists in the next month" in new Setup {
          await(repository.isDataPresent(currentFileName(date.plusMonths(1)))) shouldBe true
        }
      }
    }

    ".get" should {
      "return None" when {
        "checking if exchange rate data exists in the next two months" in new Setup {
          await(repository.get(currentFileName(date.plusMonths(2)))) shouldBe None
        }
      }

      "return exchange rate object" when {
        "checking if exchange rate data exists in the current month" in new Setup {
          await(repository.get(currentFileName(date))) shouldBe Some(exchangeRateObject)
        }

        "checking if exchange rate data exists in the next month" in new Setup {
          await(repository.get(currentFileName(date.plusMonths(1)))) shouldBe Some(
            exchangeRateObject.copy(fileName = currentFileName(date.plusMonths(1)))
          )
        }
      }
    }

    ".update" should {
      "update exchange rate data" when {
        "current month" in new Setup {
          repository.update(exchangeRateData = updatedExchangeRateDataJson) shouldBe ()
        }

        "next month" in new Setup {
          repository.update(exchangeRateData = updatedExchangeRateDataJson, forNextMonth = true) shouldBe ()
        }
      }
    }

    ".insertOrUpdate" should {
      "return None" when {
        "inserting exchange rate data for current month where no data exists" in {
          await(repository.insertOrUpdate(exchangeRateDataJson)) shouldBe None
        }

        "updating exchange rate data for current month where data exists" in new Setup {
          await(repository.insertOrUpdate(updatedExchangeRateDataJson)) shouldBe None
        }

        "a six month old exchange rate data exists" in {
          val monthsToSubtract: Long = 6

          await(
            insert(
              exchangeRateObject.copy(
                fileName = currentFileName(date.minusMonths(monthsToSubtract))
              )
            )
          )

          await(repository.insertOrUpdate(exchangeRateDataJson)) shouldBe None
        }
      }
    }
  }
}
