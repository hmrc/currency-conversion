/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{FindOneAndUpdateOptions, ReturnDocument, Updates}
import play.api.i18n.Lang.logger.logger
import play.api.libs.json.JsObject
import uk.gov.hmrc.currencyconversion.models.ExchangeRateObject
import uk.gov.hmrc.currencyconversion.utils.MongoIdHelper.currentFileName
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{implicitConversions, postfixOps}
import scala.util.Try

@Singleton
class DefaultExchangeRateRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[ExchangeRateObject](
      collectionName = "exchangeCurrencyData",
      mongoComponent = mongoComponent,
      domainFormat = ExchangeRateObject.format,
      indexes = Seq()
    )
    with ExchangeRateRepository {

  private def date = LocalDate.now()

  def get(fileName: String): Future[Option[ExchangeRateObject]] =
    collection.find(equal("_id", Codecs.toBson(fileName))).headOption()

  def isDataPresent(fileName: String): Future[Boolean] = {
    val existingData = get(fileName)
    existingData.map(value => value.isDefined)
  }

  def insert(exchangeRateData: JsObject, forNextMonth: Boolean = false): Unit =
    Try {
      val data = ExchangeRateObject(mongoId(forNextMonth), exchangeRateData)
      collection.insertOne(data).toFuture()
      if (forNextMonth) {
        // Not really a warning, but this is the only way to generate alerts in Pager Duty without changing PROD log level to INFO
        logger.warn(
          s"XRS_FILE_INSERTED_FOR_NEXT_MONTH [ExchangeRateRepository] writing to mongo is successful ${mongoId(forNextMonth)}"
        )
      } else {
        logger.info(s"[ExchangeRateRepository] writing to mongo is successful ${mongoId(forNextMonth)}")
      }
    }
      .getOrElse {
        logger.error(s"XRS_FILE_CANNOT_BE_WRITTEN_ERROR [ExchangeRateRepository] " + s"writing to mongo is failed ")
        throw new Exception(s"unable to insert exchangeRateRepository")
      }

  private def mongoId(forNextMonth: Boolean) =
    if (forNextMonth) currentFileName(date.plusMonths(1)) else currentFileName()

  def update(exchangeRateData: JsObject, forNextMonth: Boolean = false): Unit =
    Try {
      collection
        .findOneAndUpdate(
          equal("_id", Codecs.toBson(mongoId(forNextMonth))),
          Updates.set("exchangeRateData", Codecs.toBson(exchangeRateData)),
          options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
        )
        .toFuture()
      logger.info(s"[ExchangeRateRepository] writing to mongo is successful ${mongoId(forNextMonth)}")
    }
      .getOrElse {
        logger.error(s"XRS_FILE_CANNOT_BE_WRITTEN_ERROR [ExchangeRateRepository] " + s"writing to mongo is failed")
        throw new Exception(s"unable to insert exchangeRateRepository ")
      }

  private def deleteOlderExchangeData() = {
    val sixMonthOldDate = LocalDate.now.minusMonths(6.toInt)
    val oldFileName     = currentFileName(sixMonthOldDate)

    collection.findOneAndDelete(equal("_id", oldFileName)).toFutureOption() map {
      case Some(_) => logger.info(s"[ExchangeRateRepository] deleting older data from mongo is successful $oldFileName")
      case _       => logger.warn(s"[ExchangeRateRepository] no older data is available")
    }
  }

  def insertOrUpdate(exchangeRateData: JsObject, forNextMonth: Boolean): Future[Any] = {
    get(mongoId(forNextMonth)) map {
      case response if response.isEmpty =>
        insert(exchangeRateData, forNextMonth)
        Future.successful(response)
      case _                            =>
        update(exchangeRateData, forNextMonth)
        Future.successful(None)
    }
    deleteOlderExchangeData()
    Future.successful(None)
  }
}

trait ExchangeRateRepository {
  def insert(data: JsObject, forNextMonth: Boolean = false): Unit

  def update(data: JsObject, forNextMonth: Boolean = false): Unit

  def get(fileName: String): Future[Option[ExchangeRateObject]]

  def insertOrUpdate(data: JsObject, forNextMonth: Boolean = false): Future[Any]

  def isDataPresent(fileName: String): Future[Boolean]
}
