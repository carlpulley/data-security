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

import org.scalacheck.{Arbitrary, Gen}

object ByteBufferGen {
  val genByteArray: Gen[Array[Byte]] = Gen.listOf(Arbitrary.arbByte.arbitrary).map(_.toArray)

  val genByteBuffer: Gen[ByteBuffer] = {
    for {
      bytes <- Gen.listOf(Arbitrary.arbByte.arbitrary)
    } yield {
      ByteBuffer.wrap(bytes.toArray)
    }
  }
}
