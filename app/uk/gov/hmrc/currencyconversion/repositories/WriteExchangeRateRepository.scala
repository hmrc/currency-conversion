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

import java.time.LocalDate
import java.io.{File, PrintWriter}

import com.google.inject.Inject
import play.api.{Configuration, Logger}

import scala.util.{Failure, Success, Try}
import play.api.libs.json.Json

class WriteExchangeRateRepository @Inject() (
  config: Configuration,
  ) extends App {

  lazy private val path = config.get[String]("xrs.file-path")

  def writeExchangeRateFile(exchangeRateData: String): Unit = {
    val pathToJson = getClass.getResource(path + "-%02d".format(LocalDate.now.getMonthValue) +
      LocalDate.now.getYear.toString.substring(2) + ".json").getPath

    val jsValue = Json parse exchangeRateData
    val exchangeRateFormatData = Json.prettyPrint(jsValue)

    val result = Try {
      val writer = new PrintWriter(new File(pathToJson))
      writer.write(exchangeRateFormatData)
      writer.close()
    }
    result match {
      case Success(_) =>
        Logger.info(" [WriteExchangeRateRepository] Writing to file successful " + pathToJson)
      case Failure(e) =>
        Logger.error(s"XRS_FILE_CANNOT_BE_WRITTEN_FAILURE  [WriteExchangeRateRepository] writing to file failed. $e")
    }
  }

  def deleteOlderFile: Unit = {
    val sixMonthOldDate = LocalDate.now.minusMonths(6)
    val deletePath = path + "-%02d".format(sixMonthOldDate.getMonthValue) +
      sixMonthOldDate.getYear.toString.substring(2) + ".json"
    val oldFilePath = new File(deletePath)
    if(oldFilePath.exists()){
      oldFilePath.delete()
      Logger.info("[WriteExchangeRateRepository] Six month old file deleted successfully " + oldFilePath)
    }
  }
}
