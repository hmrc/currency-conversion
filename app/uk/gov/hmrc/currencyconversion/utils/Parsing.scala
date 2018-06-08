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

package uk.gov.hmrc.currencyconversion.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.Logger
import uk.gov.hmrc.currencyconversion.models.ConversionRatePeriod

import scala.xml.Elem

object Parsing {

  private val formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy")

  def ratesFromXml(exchangeRatesRoot: Elem): Option[ConversionRatePeriod] = {
    val periodAttr = (exchangeRatesRoot \ "@Period").text

    val groupedRates = {
      (exchangeRatesRoot \ "_").map { exchangeRate =>
        (exchangeRate \ "currencyCode").text -> (exchangeRate \ "rateNew").text
      }
    }.toMap

    def parseDates(dateRange: String): Option[(LocalDate, LocalDate)] = {
      dateRange.split("to").map(_.trim) match {
        case Array(start, end) => Some((LocalDate.parse(start, formatter), LocalDate.parse(end, formatter)))
        case _ => None
      }
    }

    val parsedDates = parseDates(periodAttr)

    parsedDates match {
      case Some((s, e)) => Some(ConversionRatePeriod(s, e, groupedRates))
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
      val exchangeRateNodes = elem.child.toList.filterNot(_.isAtom)
      val exchangeRateLabels = exchangeRateNodes.map(exchangeRate => exchangeRate.child.filterNot(_.isAtom).map(childNode => childNode.label).toList)

      val hasCorrectNodes = exchangeRateLabels.exists(x => x.contains("currencyCode") && x.contains("rateNew"))
      val nodesHaveContent = exchangeRateNodes.forall(x => (x \ "currencyCode").text.nonEmpty && (x \ "rateNew").text.nonEmpty)

      hasCorrectNodes && nodesHaveContent
    }

    validatePeriodAttribute && validateEssentialElems
  }
}
