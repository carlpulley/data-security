/**
 * Copyright [2020] [Carl Pulley]
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
package uk.acmelabs.datasecurity

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy

object TestSetup {
  private val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  val iamSetup =
    new IAMSetup(new AwsConfig {
      override def awsClientConfig: ClientOverrideConfiguration = {
        ClientOverrideConfiguration
          .builder
          .apiCallAttemptTimeout(Duration.of(1, ChronoUnit.SECONDS))
          .apiCallTimeout(Duration.of(10, ChronoUnit.SECONDS))
          .retryPolicy(awsClientRetryPolicy)
          .addMetricPublisher(awsMetricPublisher)
          .build
      }

      override def awsClientRetryPolicy: RetryPolicy = {
        val backoff =
          FullJitterBackoffStrategy
            .builder
            .baseDelay(Duration.of(200, ChronoUnit.MILLIS))
            .maxBackoffTime(Duration.of(10, ChronoUnit.SECONDS))
            .build

        RetryPolicy
          .builder
          .backoffStrategy(backoff)
          .numRetries(5)
          .build
      }
    })(ec)
}
