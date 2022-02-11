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

package uk.gov.hmrc.currencyconversion.binders

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, ResolverStyle}
import play.api.mvc.PathBindable
import scala.util.Try

object DateBinder {

  implicit def bindableDate(implicit stringBinder: PathBindable[String]): PathBindable[LocalDate] = new PathBindable[LocalDate] {

    private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

    override def bind(key: String, value: String): Either[String, LocalDate] = {
      for {
        dateString <- stringBinder.bind(key, value).right
        date <- Try(LocalDate.parse(dateString, formatter)).toOption.toRight(s"Cannot parse date: $dateString").right
      } yield date
    }

    override def unbind(key: String, localDate: LocalDate): String = {
      localDate.toString
    }
  }
}
