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
package uk.acmelabs.datasecurity.aws.api.model

import org.junit.runner.RunWith
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class PlaintextDataKeyTest
  extends AnyFreeSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks {

  import PlaintextDataKeyGen._

  "Expected plaintext data key specification" in {
    forAll(genPlaintextDataKey) { key =>
      key.getFormat shouldEqual "RAW"
      key.getAlgorithm shouldEqual "AES/GCM/NoPadding"
      key.ivLength() shouldEqual 16
      key.tagLength() shouldEqual 128
    }
  }

  "Expected plaintext data key destroy behaviour" in {
    val nonEmptyPlaintextDataKeyGen = genPlaintextDataKey.suchThat(_.getEncoded.length > 0)

    forAll(nonEmptyPlaintextDataKeyGen.suchThat(_.getEncoded.max > 0)) { key =>
      key.isDestroyed should be(false)

      key.destroy()

      key.isDestroyed should be(true)

      val encodedKey = key.getEncoded
      (0 until encodedKey.length).foreach { index =>
        encodedKey(index) should be(0.toByte)
      }
    }
  }
}
