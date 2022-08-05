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

package uk.gov.hmrc.currencyconversion.utils

import org.scalatest.matchers.must.Matchers._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class MongoIdHelperSpec extends AnyWordSpec with Matchers {

  val jan2011 = LocalDate.of(2011, 1, 12)
  val dec2018 = LocalDate.of(2018, 12, 12)

  "MongoIdHelper" should {
    "get this jan 2011 current file name" in {
      MongoIdHelper.currentFileName(jan2011) mustBe "exrates-monthly-0111"
    }

    "get this dec 2018 current file name" in {
      MongoIdHelper.currentFileName(dec2018) mustBe "exrates-monthly-1218"
    }
  }
}
