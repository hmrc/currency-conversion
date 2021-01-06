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

package uk.gov.hmrc.currencyconversion.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.Logger
import uk.gov.hmrc.currencyconversion.models.{Currency, CurrencyPeriod}

import scala.xml.Elem

object CurrencyParsing {

  private val formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy")

  def currenciesFromXml(exchangeRatesRoot: Elem): Option[CurrencyPeriod] = {
    val periodAttr = (exchangeRatesRoot \ "@Period").text

    val currencies = (exchangeRatesRoot \ "_").map { node =>
      val countryName: String = (node \ "countryName").text
      val currencyName: String = (node \ "currencyName").text
      val currencyCode: String = (node \ "currencyCode").text

      Currency(countryName, currencyName, currencyCode)
    }

    def parseDates(dateRange: String): Option[(LocalDate, LocalDate)] = dateRange.split("to").map(_.trim) match {
        case Array(start, end) => Some((LocalDate.parse(start, formatter), LocalDate.parse(end, formatter)))
        case _ => None
    }

    val parsedDates = parseDates(periodAttr)

    parsedDates match {
      case Some((s, e)) => Some(CurrencyPeriod(s, e, currencies))
      case None =>
        Logger.warn("Unable to parse dates from xml Element")
        None
    }
  }

  def isValidXmlElem(elem: Elem): Boolean = {
    val validatePeriodAttribute = {
      val periodRegex = "\\d{2}\\/[a-zA-Z]{3}\\/\\d{4}\\s+to\\s+\\d{2}\\/[a-zA-Z]{3}\\/\\d{4}"
      val periodAttr = (elem \ "@Period").toList.map(_.text)
      periodAttr.forall(p => p.matches(periodRegex))
    }

    val validateEssentialElems = {
      // get all the nodes and node labels that actually have values
      val exchangeRateNodes = elem.child.toList.filterNot(_.isAtom)
      val exchangeRateLabels = exchangeRateNodes.map(exchangeRate => exchangeRate.child.filterNot(_.isAtom).map(childNode => childNode.label).toList)

      val hasCorrectNodes = exchangeRateLabels.exists(x => x.contains("countryName")
        && x.contains("currencyName")
        && x.contains("currencyCode"))

      val nodesHaveContent = exchangeRateNodes.forall(x => (x \ "countryName").text.nonEmpty
        && (x \ "currencyName").text.nonEmpty
        && (x \ "currencyCode").text.nonEmpty)

      hasCorrectNodes && nodesHaveContent
    }

    validatePeriodAttribute && validateEssentialElems
  }
}
