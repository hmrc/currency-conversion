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

package uk.gov.hmrc.currencyconversion.binders

import java.time.LocalDate

import uk.gov.hmrc.play.test.UnitSpec

class BinderSpec extends UnitSpec{

  "Calling bindableDate.bind" should {

    "return a valid LocalDate" in {

      val date = "2018-02-01"
      DateBinder.bindableDate.bind("key", date) shouldBe Right(LocalDate.parse("2018-02-01"))
    }

    "return error when not a valid LocalDate" in {

      val date = "01-02-2018"
      DateBinder.bindableDate.bind("key", date) shouldBe Left(s"Cannot parse date: $date")
    }

  }

  "Calling bindableDate.unbind" should {

    "turn a LocalDate into a string" in {

      val localDate = LocalDate.parse("2018-02-01")
      DateBinder.bindableDate.unbind("key", localDate) shouldBe "2018-02-01"
    }
  }

}
