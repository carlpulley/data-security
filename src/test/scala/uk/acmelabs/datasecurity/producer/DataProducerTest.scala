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
package uk.acmelabs.datasecurity.producer

import java.util.concurrent.CompletableFuture

import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import software.amazon.awssdk.services.iam.model.Role
import uk.acmelabs.datasecurity.api.{ByteBufferGen, CMKGen}

@RunWith(classOf[JUnitRunner])
class DataProducerTest
  extends AnyFreeSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks {

  import ByteBufferGen._
  import CMKGen._

  val genRole: Gen[Role] =
    for {
      name <- Gen.identifier
    } yield Role.builder().roleName(name).build()
  val genProducerConfig: Gen[ProducerConfig] =
    for {
      role <- genRole
    } yield new ProducerConfig {
      override def encryptRole(): Role = role
    }

  "send parameter validation" - {
    "data ByteBuffers must be read-only" in {
      forAll(genProducerConfig, genByteBuffer, genCMK) { case (config, data, cmk) =>
        val producer = new DataProducer(_ => CompletableFuture.completedFuture(null), config)

        assertThrows[AssertionError] {
          producer.send(data, cmk)
        }
      }
    }
  }
}
