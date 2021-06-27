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

import akka.stream.Materializer
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.{JsSuccess, JsValue, Reads}
import uk.gov.hmrc.currencyconversion.models.{ConversionRatePeriod, Currency, CurrencyPeriod}

import java.time.LocalDate
import play.api.{Configuration, Environment, Logger}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import javax.inject.Inject
import play.api.libs.json.Json
import play.api.libs.json.OFormat.oFormatFromReadsAndOWrites

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps


class ConversionRatePeriodJson @Inject()(environment: Environment, config: Configuration,
                                         writeExchangeRateRepository: ExchangeRateRepository)
                                        (implicit ec: ExecutionContext, m: Materializer) extends ConversionRatePeriodRepository {

  case class ExchangeRate(
                           validFrom: LocalDate,
                           validTo: LocalDate,
                           currencyCode: String,
                           exchangeRate: BigDecimal,
                           currencyName: String
                         )

  object ExchangeRate {

    implicit lazy val reads: Reads[ExchangeRate] = (
        (__ \ "validFrom").read[LocalDate] and
        (__ \"validTo").read[LocalDate] and
        (__ \ "currencyCode").read[String] and
        (__ \ "exchangeRate").read[BigDecimal] and
        (__ \ "currencyName").read[String]
      )(ExchangeRate.apply _)

    implicit lazy val writes: OWrites[ExchangeRate] = Json.writes[ExchangeRate]
  }
  case class ExchangeRateData(
                           timestamp: String,
                           correlationId: String,
                           exchangeData: Seq[ExchangeRate]
                         )

  object ExchangeRateData {

    implicit lazy val reads: Reads[ExchangeRateData] = (
        (__  \ "timestamp").read[String] and
        (__  \"correlationId").read[String] and
        (__  \ "exchangeRates").read[Seq[ExchangeRate]]
      )(ExchangeRateData.apply _)

    implicit lazy val writes: OWrites[ExchangeRateData] = Json.writes[ExchangeRateData]
  }

  private def getExchangeRateFileName(date : LocalDate) : String = {
    val targetFileName = "exrates-monthly-%02d".format(date.getMonthValue) +
      date.getYear.toString.substring(2)

    if (!writeExchangeRateRepository.isDataPresent(targetFileName)) {
      targetFileName
    } else {
      Logger.info(s"$targetFileName is not present")
      "empty"
    }
  }

  /*
  private def getExchangeRates(filePath: String): Map[String, Option[BigDecimal]] = {

    getExchangeRates1(filePath)

    def getMinimumDecimalScale(rate : BigDecimal) : BigDecimal = {
      if (rate.scale < 2) rate.setScale(2) else rate
    }

    lazy val conversionRatePeriods: Seq[Map[String, Option[BigDecimal]]] = environment.resourceAsStream("json/exrates-monthly-0621.json") match {
      case Some(stream) => {
        val jsVal : JsValue = Json.parse(stream)("exchangeRates")
        jsVal.validate[Seq[ExchangeRate]] match {
          case JsSuccess(seq, _) => seq map { xrsResponse =>
            Map(xrsResponse.currencyCode -> Some(getMinimumDecimalScale(xrsResponse.exchangeRate)))
          }
          case _ => {
            Logger.error(s"XRS_FILE_CANNOT_BE_READ_ERROR [ConversionRatePeriodJson] Exchange rate file is not able to read.")
            throw new RuntimeException("Exchange rate file is not able to read.")
          }
        }
      }
      case _ => {
        Logger.error(s"XRS_FILE_CANNOT_BE_READ_ERROR [ConversionRatePeriodJson] Exchange rate file is not able to read.")
        throw new RuntimeException("Exchange rate file is not able to read.")
      }
    }
    conversionRatePeriods.flatten.toMap
  }*/

  private def getExchangeRates(filePath: String) = {

    def getMinimumDecimalScale(rate: BigDecimal): BigDecimal = {
      if (rate.scale < 2) rate.setScale(2) else rate
    }

    val exchangeRates : Future[ExchangeRateData] = writeExchangeRateRepository.get(filePath)
      .map {
        case response if response.isEmpty => {
          Logger.error(s"XRS_FILE_CANNOT_BE_READ_ERROR [ConversionRatePeriodJson] Exchange rate file is not able to read.")
          throw new RuntimeException("Exchange rate file is not able to read.")
        }
        case  response => {
          response.get.exchangeRateData.validate[ExchangeRateData] match {
            case JsSuccess(seq, _) => seq
            case _ => {
              Logger.error(s"XRS_FILE_CANNOT_BE_READ_ERROR [ConversionRatePeriodJson] Exchange rate file is not able to read.")
              throw new RuntimeException("Exchange rate file is not able to read.")
            }
            }
         }
        }

    Await.ready(exchangeRates, 2 seconds)

    val result: Seq[Map[String, Option[BigDecimal]]] = exchangeRates.value.get.get.exchangeData map { data =>
      Map(data.currencyCode -> Some(getMinimumDecimalScale(data.exchangeRate)))
    }

    result.flatten.toMap
}

  private def getCurrencies(filePath: String): Seq[Currency] = {
    environment.resourceAsStream(filePath) match {
      case Some(stream) => {
        val jsVal : JsValue = Json.parse(stream)("exchangeRates")
        jsVal.validate[Seq[ExchangeRate]] match {
          case JsSuccess(seq, _) => seq map { xrsResponse =>
            Currency("", xrsResponse.currencyCode, xrsResponse.currencyName)
          }
          case _ => {
            Logger.error(s"XRS_FILE_CANNOT_BE_READ_ERROR [ConversionRatePeriodJson] Exchange rate file is not able to read.")
            throw new RuntimeException("Exchange rate file is unable to read.")
          }
        }
      }
      case _ => {
        Logger.error(s"XRS_FILE_CANNOT_BE_READ_ERROR [ConversionRatePeriodJson] Exchange rate file is not able to read.")
        throw new RuntimeException("Exchange rate file is unable to read.")
      }
    }
  }

  def getConversionRatePeriod(date: LocalDate): Option[ConversionRatePeriod] = {
    val fileName = getExchangeRateFileName(date)
    if (fileName.equals("empty")) {
       Option.empty
    } else {
      val rates : Map[String, Option[BigDecimal]] = getExchangeRates(fileName)
      Some(ConversionRatePeriod(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()),
        None, rates))
    }
  }

  def getLatestConversionRatePeriod(date: LocalDate): ConversionRatePeriod = {
    val fileName = getExchangeRateFileName(date)
    if (fileName.equals("empty")) {
      Logger.error(s"XRS_FILE_NOT_AVAILABLE_ERROR [ConversionRatePeriodJson] Exchange rate file is not available.")
      throw new RuntimeException("Exchange rate file is not able to read.")
    } else {
      val rates : Map[String, Option[BigDecimal]] = getExchangeRates(fileName)
      ConversionRatePeriod(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()),
        None, rates)
    }
  }

  def getCurrencyPeriod(date: LocalDate): Option[CurrencyPeriod] = {
    getExchangeRateFileName(date)
    val currencies : Seq[Currency] = getCurrencies(getExchangeRateFileName(date))
    Some(CurrencyPeriod(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()), currencies))
  }
}
