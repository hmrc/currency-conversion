/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.currencyconversion.models

import play.api.libs.json.{Format, JsObject, OFormat, OWrites, Reads, __}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class ExchangeRateObject(fileName: String, exchangeRateData: JsObject)

object ExchangeRateObject {
  given formatInstant: Format[Instant] = MongoJavatimeFormats.instantFormat

  given reads: Reads[ExchangeRateObject] = {

    import play.api.libs.functional.syntax.*

    (
      (__ \ "_id").read[String] and
        (__ \ "exchangeRateData").read[JsObject]
    )(ExchangeRateObject.apply)
  }

  given writes: OWrites[ExchangeRateObject] = {

    import play.api.libs.functional.syntax.*

    (
      (__ \ "_id").write[String] and
        (__ \ "exchangeRateData").write[JsObject]
    )(o => Tuple.fromProductTyped(o))
  }

  given format: OFormat[ExchangeRateObject] = OFormat(
    reads,
    writes
  )
}
