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

package uk.gov.hmrc.currencyconversion.services

import java.io.InputStream
import java.time.LocalDate

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.currencyconversion.repositories.ExchangeRateRepository

import scala.xml.{Elem, XML}




object ExchangeRatesService {


  def getRate(date: LocalDate, currencyCode: String): Option[JsObject] = {

    for {
      crp <- ExchangeRateRepository.getRecentConversionRatePeriod(date)
      rate <- crp.rates.get(currencyCode)
    } yield Json.obj("startDate" -> crp.startDate, "endDate" -> crp.endDate, "rate" -> rate)
  }


  def loadXmlFiles: Seq[Elem] = {


    //TODO: Define here how many months worth of files we want to collect
    def xmlStreams(month: Int = 2, year: Int = 18): Stream[InputStream] = {
      val nextMonth = if (month == 12) 1 else month + 1
      val nextYear = if (month == 12) year + 1 else year

      val file = "/resources/xml/exrates-monthly-" + "%02d".format(month) + year + ".xml"

      val inputStream: Option[InputStream] = Option(getClass.getResourceAsStream(file))

      inputStream match {
        case Some(resource) => resource #:: xmlStreams(nextMonth, nextYear)
        case None => Stream.empty
      }
    }

    //TODO Add validation, log error if invalid, return all valid files
    xmlStreams().map(XML.load)
  }
}
