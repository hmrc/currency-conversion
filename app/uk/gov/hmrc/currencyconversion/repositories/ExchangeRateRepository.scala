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
import uk.gov.hmrc.currencyconversion.models.ExchangeRateObject
import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{FindOneAndUpdateOptions, ReturnDocument, Updates}
import play.api.i18n.Lang.logger.logger
import play.api.libs.json.JsObject
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.{implicitConversions, postfixOps}

@Singleton
class DefaultExchangeRateRepository @Inject() (
                                                mongoComponent: MongoComponent,
  ) (implicit ec: ExecutionContext, m: Materializer) extends PlayMongoRepository[ExchangeRateObject](
  collectionName = "exchangeCurrencyData",
  mongoComponent = mongoComponent,
  domainFormat   = ExchangeRateObject.format,
  indexes = Seq())
  with ExchangeRateRepository {


  private def date = LocalDate.now()
  private def currentFileName: String = "exrates-monthly-%02d".format(date.getMonthValue) +
    date.getYear.toString.substring(2)


  def get(fileName: String): Future[Option[ExchangeRateObject]] =
    collection.find(equal("_id" , Codecs.toBson(fileName))).headOption()


  def isDataPresent(fileName: String): Boolean = {
    val existingData = get(fileName)
    Await.ready(existingData, 3 second)
    existingData.value.get.get.isEmpty
  }

  def insert(exchangeRateData: JsObject): Future[Unit] = {
    val data = ExchangeRateObject(currentFileName, exchangeRateData)

    collection.insertOne(data).toFuture() map { result =>
      logger.info(s"[ExchangeRateRepository] writing to mongo is successful $currentFileName")
    } recover {
      logger.error(s"XRS_FILE_CANNOT_BE_WRITTEN_ERROR [ExchangeRateRepository] " + s"writing to mongo is failed ")
      throw new Exception(s"unable to insert exchangeRateRepository")
    }
  }

  def update(exchangeRateData: JsObject): Future[ExchangeRateObject] = {

    val data = ExchangeRateObject(currentFileName, exchangeRateData)

    collection.findOneAndUpdate(equal("_id", Codecs.toBson(data.fileName)),
      Updates.set("exchangeRateData",Codecs.toBson(data)),
      options = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)).toFuture() map   { result =>
      logger.info(s"[ExchangeRateRepository] writing to mongo is successful $currentFileName")
        result
    } recover {
      logger.error(s"XRS_FILE_CANNOT_BE_WRITTEN_ERROR [ExchangeRateRepository] " + s"writing to mongo is failed")
      throw new Exception(s"unable to insert exchangeRateRepository ")
    }
  }


  private def deleteOlderExchangeData() = {
    val sixMonthOldDate = LocalDate.now.minusMonths(6.toInt)
    val oldFileName = "exrates-monthly-%02d".format(sixMonthOldDate.getMonthValue) +
      sixMonthOldDate.getYear.toString.substring(2)

    collection.findOneAndDelete(equal("_id", oldFileName)).toFuture() map {
      case result => logger.info(s"[ExchangeRateRepository] deleting older data from mongo is successful $oldFileName")
      case _ => logger.info(s"[ExchangeRateRepository] no older data is available")
    }
  }

  def insertOrUpdate(exchangeRateData: JsObject): Future[Any] = {
    get(currentFileName) map {
      case response if response.isEmpty => insert(exchangeRateData)
        response
      case _ => update(exchangeRateData)
        Future.successful(None)
    }
    deleteOlderExchangeData()
    Future.successful(None)
  }
}

trait ExchangeRateRepository {
  def insert(data: JsObject): Future[Unit]
  def update(data: JsObject): Future[ExchangeRateObject]
  def get(fileName: String): Future[Option[ExchangeRateObject]]
  def insertOrUpdate(data: JsObject):Future[Any]
  def isDataPresent(fileName: String): Boolean
}
