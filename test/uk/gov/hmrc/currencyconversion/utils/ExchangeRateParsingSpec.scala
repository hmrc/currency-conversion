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

package uk.gov.hmrc.currencyconversion.utils

import java.time.LocalDate

import org.scalatest.{Matchers, WordSpec}

class ExchangeRateParsingSpec extends WordSpec with Matchers {

  "ratesFromXml" should {

    "parse xml representing currency rates" in {

      val xml =
        <exchangeRateMonthList Period="01/May/2018 to 31/May/2018">
          <exchangeRate>
            <countryName>Argentina</countryName>
            <countryCode>AR</countryCode>
            <currencyName>Peso </currencyName>
            <currencyCode>ARS</currencyCode>
            <rateNew>28.67</rateNew>
          </exchangeRate>
          <exchangeRate>
            <countryName>Australia</countryName>
            <countryCode>AU</countryCode>
            <currencyName>Dollar </currencyName>
            <currencyCode>AUD</currencyCode>
            <rateNew>1.782</rateNew>
          </exchangeRate>
        </exchangeRateMonthList>

      val optionOfSample = ExchangeRateParsing.ratesFromXml(xml)

      val resultOfSample = optionOfSample match {
        case Some(x) => x
        case None => fail("Result returned as None")
      }

      resultOfSample.startDate shouldBe LocalDate.parse("2018-05-01")
      resultOfSample.endDate shouldBe LocalDate.parse("2018-05-31")
      resultOfSample.rates shouldBe Map("ARS" -> Some(28.67), "AUD" -> Some(1.782))
    }


    "enforce a minimum of two decimal places" in {

      val xml =
        <exchangeRateMonthList Period="01/May/2018 to 31/May/2018">
          <exchangeRate>
            <countryName>Argentina</countryName>
            <countryCode>AR</countryCode>
            <currencyName>Peso </currencyName>
            <currencyCode>ARS</currencyCode>
            <rateNew>28</rateNew>
          </exchangeRate>
          <exchangeRate>
            <countryName>Australia</countryName>
            <countryCode>AU</countryCode>
            <currencyName>Dollar </currencyName>
            <currencyCode>AUD</currencyCode>
            <rateNew>1.7</rateNew>
          </exchangeRate>
        </exchangeRateMonthList>

      val optionOfSample = ExchangeRateParsing.ratesFromXml(xml)

      val resultOfSample = optionOfSample match {
        case Some(x) => x
        case None => fail("Result returned as None")
      }

      resultOfSample.startDate shouldBe LocalDate.parse("2018-05-01")
      resultOfSample.endDate shouldBe LocalDate.parse("2018-05-31")
      resultOfSample.rates shouldBe Map("ARS" -> Some(28.00), "AUD" -> Some(1.70))
    }
  }

  "isValidXml" should {

    "return true for valid xml" in {

      val xml =
        <exchangeRateMonthList Period="01/May/2018 to 31/May/2018">
          <exchangeRate>
            <countryName>Argentina</countryName>
            <countryCode>AR</countryCode>
            <currencyName>Peso </currencyName>
            <currencyCode>ARS</currencyCode>
            <rateNew>28.5</rateNew>
          </exchangeRate>
          <exchangeRate>
            <countryName>Australia</countryName>
            <countryCode>AU</countryCode>
            <currencyName>Dollar </currencyName>
            <currencyCode>AUD</currencyCode>
            <rateNew>1.782</rateNew>
          </exchangeRate>
          <exchangeRate>
            <countryName>Brazil</countryName>
            <countryCode>BR</countryCode>
            <currencyName>Real </currencyName>
            <currencyCode>BRL</currencyCode>
            <rateNew>4.5523</rateNew>
          </exchangeRate>
        </exchangeRateMonthList>

      ExchangeRateParsing.isValidXmlElem(xml) shouldBe true
    }

    "validate the period attribute of an xml element" in {

      val invalidPeriodXml =
        <exchangeRateMonthList Period="01-May-2018 to 31-5-2020">
          <exchangeRate>
            <countryName>Argentina</countryName>
            <countryCode>AR</countryCode>
            <currencyName>Peso </currencyName>
            <currencyCode>ARS</currencyCode>
          </exchangeRate>
        </exchangeRateMonthList>

      ExchangeRateParsing.isValidXmlElem(invalidPeriodXml) shouldBe false
    }

    "validate the existence of essential child elements" in {

      val xmlMissingCurrencyCode =
        <exchangeRateMonthList Period="01/May/2018 to 31/May/2018">
          <exchangeRate>
            <countryName>Argentina</countryName>
            <countryCode>AR</countryCode>
            <currencyName>Peso </currencyName>
            <rateNew>28.5</rateNew>
          </exchangeRate>
          <exchangeRate>
            <countryName>Hong Kong</countryName>
            <countryCode>HK</countryCode>
            <currencyName>Dollar </currencyName>
            <currencyCode>HKD</currencyCode>
            <rateNew>10.93</rateNew>
          </exchangeRate>
        </exchangeRateMonthList>

      val xmlMissingRateNew =
        <exchangeRateMonthList Period="01/May/2018 to 31/May/2018">
          <exchangeRate>
            <countryName>Argentina</countryName>
            <countryCode>AR</countryCode>
            <currencyName>Peso </currencyName>
            <currencycode>ARS</currencycode>
            <rateNew>28.5</rateNew>
          </exchangeRate>
          <exchangeRate>
            <countryName>Hong Kong</countryName>
            <countryCode>HK</countryCode>
            <currencyName>Dollar </currencyName>
            <currencyCode>HKD</currencyCode>
          </exchangeRate>
        </exchangeRateMonthList>

      ExchangeRateParsing.isValidXmlElem(xmlMissingCurrencyCode) shouldBe false
      ExchangeRateParsing.isValidXmlElem(xmlMissingRateNew) shouldBe false
    }

    "validate essential elements have content" in {

      val xmlEmptyRateNew =
        <exchangeRateMonthList Period="01/May/2018 to 31/May/2018">
          <exchangeRate>
            <countryName>Argentina</countryName>
            <countryCode>AR</countryCode>
            <currencyName>Peso </currencyName>
            <rateNew></rateNew>
            <currencyCode>HKD</currencyCode>
          </exchangeRate>
          <exchangeRate>
            <countryName>Hong Kong</countryName>
            <countryCode>HK</countryCode>
            <currencyName>Dollar </currencyName>
            <currencyCode>HKD</currencyCode>
            <rateNew>10.93</rateNew>
          </exchangeRate>
        </exchangeRateMonthList>

      val xmlEmptyCurrencyCode =
        <exchangeRateMonthList Period="01/May/2018 to 31/May/2018">
          <exchangeRate>
            <countryName>Argentina</countryName>
            <countryCode>AR</countryCode>
            <currencyName>Peso </currencyName>
            <rateNew>28.25</rateNew>
            <currencyCode>HKD</currencyCode>
          </exchangeRate>
          <exchangeRate>
            <countryName>Hong Kong</countryName>
            <countryCode>HK</countryCode>
            <currencyName>Dollar </currencyName>
            <currencyCode></currencyCode>
            <rateNew>10.93</rateNew>
          </exchangeRate>
        </exchangeRateMonthList>

      ExchangeRateParsing.isValidXmlElem(xmlEmptyRateNew) shouldBe false
      ExchangeRateParsing.isValidXmlElem(xmlEmptyCurrencyCode) shouldBe false
    }
  }
}

