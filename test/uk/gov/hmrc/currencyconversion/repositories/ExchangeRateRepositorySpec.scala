/*
 * Copyright 2021 HM Revenue & Customs
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

import java.io.{File, PrintWriter}
import java.time.LocalDate

import org.scalatest.Inspectors._
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting

class ExchangeRateRepositorySpec extends WordSpec with GuiceOneAppPerSuite with Matchers with Injecting {

  override lazy val app: Application = {
    new GuiceApplicationBuilder()
      .configure(
        "xrs.file-path" -> "test/resources/json/exrates-monthly"
      )
      .build()
  }

  private lazy val writeExchangeRateRepository: WriteExchangeRateRepository = inject[WriteExchangeRateRepository]

    "the rates files stored in memory" should {

      "all have the correct format file name" in {

        val fileNames = new File(getClass.getResource("/resources/json/").toURI).list().toList

        forAll(fileNames) { fileName =>
          fileName should fullyMatch regex """exrates-monthly-\d{4}.json"""
        }
      }

      /*"be written correctly" in {
        val data =
        """{"timestamp":"2021-06-15T15:41:38Z",
          |"correlationId":"72a89d23-0fc6-4212-92fc-ea8b05139c76",
          |"exchangeRates":[{"validFrom":"2021-06-15","validTo":"2021-06-15","currencyCode":"ARS","exchangeRate":133.25,"currencyName":"Peso"}]}"""
          .stripMargin

        writeExchangeRateRepository.writeExchangeRateFile(data)

        val createdFilePath = "test/resources/json/exrates-monthly-%02d".format(LocalDate.now.getMonthValue) +
          LocalDate.now.getYear.toString.substring(2) + ".json"
        val path = new File(createdFilePath)
        path.canRead shouldBe true
        path.exists() shouldBe true
        path.delete()
      }*/

      "delete six month older file" in {
        val data =
          """{"timestamp":"2021-06-15T15:41:38Z",
            |"correlationId":"72a89d23-0fc6-4212-92fc-ea8b05139c76",
            |"exchangeRates":[{"validFrom":"2021-06-15","validTo":"2021-06-15","currencyCode":"ARS","exchangeRate":133.25,"currencyName":"Peso"}]}"""
            .stripMargin

        val sixMonthOldDate = LocalDate.now.minusMonths(6)
        val deletePath = "test/resources/json/exrates-monthly-" + "%02d".format(sixMonthOldDate.getMonthValue) +
          sixMonthOldDate.getYear.toString.substring(2) + ".json"
        val oldFilePath = new File(deletePath)
        val writer = new PrintWriter(oldFilePath)
        writer.write(data)
        writer.close()

        writeExchangeRateRepository.deleteOlderFile
        oldFilePath.exists() shouldBe false
      }
  }
}

