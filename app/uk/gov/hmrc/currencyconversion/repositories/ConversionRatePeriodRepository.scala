/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.gov.hmrc.currencyconversion.models.{ConversionRatePeriod, CurrencyPeriod}
import uk.gov.hmrc.currencyconversion.utils.{CurrencyParsing, ExchangeRateParsing}

import scala.xml.XML

class ConversionRatePeriodRepository {

  private def xmlStreams(month: Int = 9, year: Int = 19): Stream[InputStream] = {
    val nextMonth = if (month == 12) 1 else month + 1
    val nextYear = if (month == 12) year + 1 else year

    val file = "/resources/xml/exrates-monthly-" + "%02d".format(month) + year + ".xml"

    val inputStream: Option[InputStream] = Option(getClass.getResourceAsStream(file))

    inputStream match {
      case Some(resource) => resource #:: xmlStreams(nextMonth, nextYear)
      case None => Stream.empty
    }
  }

  lazy val conversionRatePeriods: Seq[ConversionRatePeriod] =
    xmlStreams().map(XML.load).flatMap(ExchangeRateParsing.ratesFromXml).reverse

  lazy val currencyPeriods: Seq[CurrencyPeriod] =
    xmlStreams().map(XML.load).flatMap(CurrencyParsing.currenciesFromXml).reverse

  def getConversionRatePeriod(date: LocalDate): Option[ConversionRatePeriod] =
    conversionRatePeriods.find(crp => (crp.startDate.isBefore(date) || crp.startDate.isEqual(date)) && (crp.endDate.isAfter(date) || crp.endDate.isEqual(date)))

  def getLatestConversionRatePeriod: ConversionRatePeriod =
    conversionRatePeriods.head

  def getCurrencyPeriod(date: LocalDate): Option[CurrencyPeriod] =
    currencyPeriods.find(cp =>
      (cp.start.isBefore(date) || cp.start.isEqual(date)) && (cp.end.isAfter(date) || cp.end.isEqual(date)))

}
