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

package uk.gov.hmrc.currencyconversion.workers

import org.scalatest.OptionValues
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.currencyconversion.utils.WireMockHelper

import java.time.LocalDateTime
import scala.concurrent.duration.FiniteDuration

class XrsDelayHelperSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with MockitoSugar
    with WireMockHelper
    with Eventually {

  private val defaultDelay = FiniteDuration(0, "seconds")

  "Calculate Initial Delay - should return a delay in minutes, for the EIS call at 12.01am" in {

    val helper = new XrsDelayHelper
    val delay  = helper.calculateInitialDelay(scheduledTime = true, defaultDelay)

    val now: LocalDateTime         = LocalDateTime.now()
    val refreshTime: LocalDateTime = now.plusMinutes(delay.toMinutes).withSecond(0).withNano(0)

    refreshTime shouldBe now.plusDays(1).withHour(0).withMinute(1).withSecond(0).withNano(0)
  }

  "Calculate Initial Delay - should return a default delay of 0 if config is false" in {

    val helper = new XrsDelayHelper
    val delay  = helper.calculateInitialDelay(scheduledTime = false, defaultDelay)

    delay.toMinutes shouldBe 0
  }
}
