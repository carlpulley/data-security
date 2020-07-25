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
package uk.acmelabs.datasecurity.aws.api.crypto

import org.junit.runner.RunWith
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.acmelabs.datasecurity.api.ByteBufferGen
import uk.acmelabs.datasecurity.aws.api.model.PlaintextDataKeyGen

@RunWith(classOf[JUnitRunner])
class DataKeyEncryptorTest
  extends AnyFreeSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks {

  import ByteBufferGen._
  import PlaintextDataKeyGen._

  "encrypt requires a readonly ByteBuffer" in {
    forAll(genPlaintextDataKey, genByteBuffer) { case (plaintextKey, data) =>
      val encryptor: DataKeyEncryptor = new DataKeyEncryptor(plaintextKey)

      assertThrows[AssertionError] {
        encryptor.encrypt(data)
      }
    }
  }

  "decrypt requires a mutable ByteBuffer" in {
    forAll(genPlaintextDataKey, genByteArray, genByteBuffer) { case (plaintextKey, encryptedBytes, plaintextData) =>
      val encryptor: DataKeyEncryptor = new DataKeyEncryptor(plaintextKey)

      assertThrows[AssertionError] {
        encryptor.decrypt(encryptedBytes, plaintextData.asReadOnlyBuffer())
      }
    }
  }
}
