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
package uk.acmelabs.datasecurity.aws.api.model;

import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.concurrent.atomic.AtomicBoolean;

import software.amazon.awssdk.core.SdkBytes;

@SuppressWarnings("serial")
public final class PlaintextDataKey implements SecretKey {

  private final ByteBuffer key;
  private final AtomicBoolean isDestroyed;

  public PlaintextDataKey(final SdkBytes key) {
    this.isDestroyed = new AtomicBoolean(false);

    final byte[] keyArray = key.asByteArray();
    this.key = ByteBuffer.allocateDirect(keyArray.length);
    this.key.put(keyArray);
  }

  public int ivLength() {
    return 16;
  }

  public int tagLength() {
    return 128;
  }

  @Override
  public String getAlgorithm() {
    return "AES/GCM/NoPadding";
  }

  @Override
  public String getFormat() {
    return "RAW";
  }

  @Override
  public byte[] getEncoded() {
    byte[] result = new byte[key.capacity()];
    for (int index = 0; index < result.length; index++) {
      result[index] = key.get(index);
    }
    return result;
  }

  @Override
  public void destroy() throws DestroyFailedException {
    try {
      for (int index = 0; index < key.capacity(); index++) {
        key.put(index, (byte) 0);
      }
      isDestroyed.set(true);
    } catch(IndexOutOfBoundsException | ReadOnlyBufferException exn) {
      throw new DestroyFailedException();
    }
  }

  @Override
  public boolean isDestroyed() {
    return isDestroyed.get();
  }
}
