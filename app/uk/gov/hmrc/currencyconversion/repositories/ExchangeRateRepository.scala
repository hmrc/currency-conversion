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

package uk.gov.hmrc.currencyconversion.repositories

import java.io.InputStream
import java.time.LocalDate

import javax.inject.Inject
import uk.gov.hmrc.currencyconversion.models.ConversionRatePeriod
import uk.gov.hmrc.currencyconversion.services.ExchangeRateService
import uk.gov.hmrc.currencyconversion.utils.ExchangeRateParsing

import scala.xml.{Elem, XML}

class ExchangeRateRepository {

  val files: Seq[ConversionRatePeriod] = loadXmlFiles.flatMap(ExchangeRateParsing.ratesFromXml)

  def loadXmlFiles: Seq[Elem] = {
    //TODO: Define here how many months worth of files we want to load
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

    //TODO Add validation from utils parsing, log error if invalid, return all valid files
    xmlStreams().map(XML.load)
  }
  def getConversionRatePeriod(date: LocalDate): Option[ConversionRatePeriod] = {
    files.find(crp => crp.startDate.isBefore(date) || crp.endDate.isAfter(date) || crp.startDate.isEqual(date) || crp.endDate.isEqual(date))
  }

  def getRecentConversionRatePeriod(date: LocalDate): Option[ConversionRatePeriod] = {
    files.find(crp => crp.startDate.isBefore(date) && crp.endDate.isBefore(date.plusMonths(2)))
  }
}
