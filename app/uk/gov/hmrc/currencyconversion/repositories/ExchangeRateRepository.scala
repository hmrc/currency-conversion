package uk.gov.hmrc.currencyconversion.repositories

import java.time.LocalDate

import uk.gov.hmrc.currencyconversion.models.ConversionRatePeriod
import uk.gov.hmrc.currencyconversion.services.ExchangeRatesService.loadXmlFiles
import uk.gov.hmrc.currencyconversion.utils.Parsing

object ExchangeRateRepository {

  val files: Seq[ConversionRatePeriod] = loadXmlFiles.flatMap(Parsing.ratesFromXml)

  def getConversionRatePeriod(date: LocalDate): Option[ConversionRatePeriod] = {
    files.find(crp => crp.startDate.isBefore(date) || crp.endDate.isAfter(date) || crp.startDate.isEqual(date) || crp.endDate.isEqual(date))
  }

  def getRecentConversionRatePeriod(date: LocalDate): Option[ConversionRatePeriod] = {
    files.find(crp => crp.startDate.isBefore(date) && crp.endDate.isBefore(date.plusMonths(2)))
  }
}
