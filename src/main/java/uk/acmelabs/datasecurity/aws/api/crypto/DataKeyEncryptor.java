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
package uk.acmelabs.datasecurity.aws.api.crypto;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import uk.acmelabs.datasecurity.aws.api.model.PlaintextDataKey;

public final class DataKeyEncryptor {

  private final PlaintextDataKey plaintextKey;
  private final byte[] iv;

  public DataKeyEncryptor(final PlaintextDataKey plaintextKey) throws NoSuchAlgorithmException {
    final SecureRandom random = SecureRandom.getInstanceStrong();

    this.plaintextKey = plaintextKey;
    this.iv = new byte[plaintextKey.ivLength()];
    random.nextBytes(iv);
  }

  public DataKeyEncryptor(final PlaintextDataKey plaintextKey, final byte[] iv) {
    this.plaintextKey = plaintextKey;
    this.iv = iv;
  }

  public byte[] getIV() {
    return iv;
  }

  public byte[] encrypt(
    final ByteBuffer data
  ) throws
    NoSuchAlgorithmException,
    NoSuchPaddingException,
    InvalidKeyException,
    InvalidAlgorithmParameterException,
    IllegalBlockSizeException,
    BadPaddingException
  {
    assert data.isReadOnly();

    final byte[] plaintextData = new byte[data.capacity()];
    final Cipher cipher = Cipher.getInstance(plaintextKey.getAlgorithm());
    final SecretKeySpec keySpec = new SecretKeySpec(plaintextKey.getEncoded(), "AES");
    final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(plaintextKey.tagLength(), iv);

    cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);

    try {
      for (int index = 0; index < plaintextData.length; index++) {
        plaintextData[index] = data.get(index);
      }

      return cipher.doFinal(plaintextData);
    } finally {
      Arrays.fill(plaintextData, (byte) 0);
    }
  }

  public void decrypt(
    final byte[] encryptedData,
    final ByteBuffer plaintextData
  ) throws
    NoSuchAlgorithmException,
    NoSuchPaddingException,
    InvalidKeyException,
    InvalidAlgorithmParameterException,
    IllegalBlockSizeException,
    BadPaddingException,
    ShortBufferException
  {
    assert !plaintextData.isReadOnly();

    final Cipher cipher = Cipher.getInstance(plaintextKey.getAlgorithm());
    final SecretKeySpec keySpec = new SecretKeySpec(plaintextKey.getEncoded(), "AES");
    final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(plaintextKey.tagLength(), iv);

    cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
    cipher.doFinal(ByteBuffer.wrap(encryptedData), plaintextData);
  }
}
