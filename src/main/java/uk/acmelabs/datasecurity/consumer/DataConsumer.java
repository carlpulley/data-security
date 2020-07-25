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
package uk.acmelabs.datasecurity.consumer;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.security.auth.DestroyFailedException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import uk.acmelabs.datasecurity.api.Message;
import uk.acmelabs.datasecurity.aws.api.crypto.DataKeyEncryptor;
import uk.acmelabs.datasecurity.aws.api.model.PlaintextDataKey;
import uk.acmelabs.datasecurity.aws.client.KMS;

final public class DataConsumer {

  private final KMS kms;
  private final Function<ByteBuffer, CompletableFuture<Void>> processor;
  private final ConsumerConfig config;

  // WARNING: processor ByteBuffer argument data will be zeroed on return from this function call!
  public DataConsumer(final Function<ByteBuffer, CompletableFuture<Void>> processor, final ConsumerConfig config) {
    this.processor = processor;
    this.config = config;
    this.kms = new KMS(config.decryptRole(), config);
  }

  public CompletableFuture<Void> receive(final Message message) {
    return
      kms
        .decryptDataKey(message.getCMK(), message.getDataKey())
        .thenComposeAsync(dataKey -> {
            try {
              final PlaintextDataKey plaintextKey = dataKey.plaintextKey();
              try {
                final ByteBuffer plaintextMessage =
                  ByteBuffer.allocateDirect(message.getData().length - message.getIV().length);
                final DataKeyEncryptor encryptor = new DataKeyEncryptor(plaintextKey, message.getIV());

                encryptor.decrypt(message.getData(), plaintextMessage);
                plaintextMessage.position(0);

                return this.processor.apply(plaintextMessage.asReadOnlyBuffer()).whenCompleteAsync((value, exn) -> {
                  for (int index = 0; index < plaintextMessage.capacity(); index++) {
                    plaintextMessage.put(index, (byte) 0);
                  }
                });
              } finally {
                plaintextKey.destroy();
              }
            } catch (NoSuchAlgorithmException
              | NoSuchPaddingException
              | InvalidKeyException
              | InvalidAlgorithmParameterException
              | IllegalBlockSizeException
              | BadPaddingException
              | ShortBufferException
              | DestroyFailedException exn
            ) {
              return CompletableFuture.failedFuture(exn);
            }
          },
          config.defaultExecutor()
        );
  }
}
