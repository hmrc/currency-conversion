/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.i18n.Lang.logger.logger

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

class XrsDelayHelper {

  def calculateInitialDelay(scheduledTime: Boolean, initialDelay: FiniteDuration): FiniteDuration =
    if (scheduledTime) {
      val now          = LocalDateTime.now().atOffset(ZoneOffset.UTC).toLocalDateTime.withSecond(0).withNano(0)
      val refreshTime  = now.plusDays(1).withHour(0).withMinute(1).withSecond(0).withNano(0)
      val refreshDelay = Duration(ChronoUnit.MINUTES.between(now, refreshTime), TimeUnit.MINUTES)
      logger.info(
        s"[XrsExchangeRateRequestWorker][calculateInitialDelay] Time of deployment: $now, Checking for rates file in ${refreshDelay.toMinutes} minutes"
      )
      refreshDelay
    } else {
      initialDelay
    }
}
