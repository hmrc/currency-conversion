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
import reactivemongo.api.WriteConcern
import uk.gov.hmrc.currencyconversion.models.ExchangeRateObject
import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.{implicitConversions, postfixOps}

@Singleton
class DefaultExchangeRateRepository @Inject() (
  mongo: ReactiveMongoApi,
  ) (implicit ec: ExecutionContext, m: Materializer) extends ExchangeRateRepository {

  private val collectionName: String = "exchangeCurrencyData"
  private val date = LocalDate.now()
  private val currentFileName: String = "exrates-monthly-%02d".format(date.getMonthValue) +
    date.getYear.toString.substring(2)

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  private val idIndex = Index(
    key     = Seq("_id" -> IndexType.Ascending),
    unique = true
  )

  val started: Future[Unit] =
    collection.flatMap {
      coll =>
        for {
          _ <- coll.indexesManager.ensure(idIndex)
        } yield ()
    }

  def get(fileName: String): Future[Option[ExchangeRateObject]] =
    collection.flatMap(_.find(Json.obj("_id" -> fileName), None).one[ExchangeRateObject])

  def isDataPresent(fileName: String): Boolean = {
    val existingData = get(fileName)
    Await.ready(existingData, 3 second)
    existingData.value.get.get.isEmpty
  }

  def insert(exchangeRateData: JsObject): Future[Any] = {
    val data = ExchangeRateObject(currentFileName, exchangeRateData)
    collection.flatMap(_.insert(ordered = true, WriteConcern.Journaled).one(data)).map {
      case wr: reactivemongo.api.commands.WriteResult if wr.writeErrors.isEmpty =>
        Logger.info(s"[ExchangeRateRepository] writing to mongo is successful $currentFileName")
        wr
      case e => Logger.error(s"XRS_FILE_CANNOT_BE_WRITTEN_ERROR [ExchangeRateRepository] " +
        s"writing to mongo is failed $e")
        throw new Exception(s"unable to insert exchangeRateRepository $e")
    }
  }

  def update(exchangeRateData: JsObject): Future[Option[ExchangeRateObject]] = {
    val data = ExchangeRateObject(currentFileName, exchangeRateData)
    val selector = Json.obj("_id" -> data.fileName)

    collection.flatMap {
      _.findAndUpdate(selector = selector, update = data, fetchNewObject = true, upsert = false, sort = None,
        fields = None, bypassDocumentValidation = false, writeConcern = WriteConcern.Acknowledged,
        maxTime = None, None, Nil) map {
        _.result[ExchangeRateObject] match {
          case success if success.isDefined => Logger.info(s"[ExchangeRateRepository] updating to mongo is successful $currentFileName")
            success
          case _ => Logger.error(s"XRS_FILE_CANNOT_BE_WRITTEN_ERROR [ExchangeRateRepository] updating to mongo is failed")
            throw new Exception(s"unable to update exchangeRateRepository")
        }
      }
    }
  }


  private def deleteOlderExchangeData():Future[Any] = {
    val sixMonthOldDate = LocalDate.now.minusMonths(6.toInt)
    val oldFileName = "exrates-monthly-%02d".format(sixMonthOldDate.getMonthValue) +
      sixMonthOldDate.getYear.toString.substring(2)

    val selector = Json.obj("_id" -> oldFileName)
    collection.flatMap {
      _.findAndRemove(selector = selector, sort = None, fields = None, writeConcern = WriteConcern.Acknowledged,
        maxTime = None, None, Nil).map {
        _.result[ExchangeRateObject] match {
          case success if success.isDefined => Logger.info(s"[ExchangeRateRepository] deleting older data from mongo is successful $oldFileName")
            success
          case _ => Logger.info(s"[ExchangeRateRepository] no older data is available")
            Future.successful(None)
        }
      }
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
  }
}

trait ExchangeRateRepository {
  val started: Future[Unit]
  def insert(data: JsObject): Future[Any]
  def update(data: JsObject): Future[Option[ExchangeRateObject]]
  def get(fileName: String): Future[Option[ExchangeRateObject]]
  def insertOrUpdate(data: JsObject):Future[Any]
  def isDataPresent(fileName: String): Boolean
}
