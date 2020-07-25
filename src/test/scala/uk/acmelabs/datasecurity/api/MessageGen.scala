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
package uk.acmelabs.datasecurity.api

import java.nio.ByteBuffer

import org.scalacheck.Gen
import uk.acmelabs.datasecurity.api.ByteBufferGen.genByteBuffer
import uk.acmelabs.datasecurity.aws.api.crypto.DataKeyEncryptor
import uk.acmelabs.datasecurity.aws.api.model.DataKey

object MessageGen {
  def genCMK(kmsData: Map[CMK, Seq[DataKey]]): Gen[CMK] = Gen.oneOf(kmsData.keys)

  def genMessage(kmsData: Map[CMK, Seq[DataKey]], data: Option[ByteBuffer] = None): Gen[(Message, ByteBuffer)] =
    for {
      cmk <- genCMK(kmsData)
      dataKey <- Gen.oneOf(kmsData(cmk))
      text <- data.fold(genByteBuffer)(Gen.const)
    } yield {
      val encryptor = new DataKeyEncryptor(dataKey.plaintextKey())

      (new Message(encryptor.encrypt(text), dataKey.encryptedKey(), encryptor.getIV, cmk), text)
    }
}
