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
package uk.acmelabs.datasecurity.aws.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.kms.KmsAsyncClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import uk.acmelabs.datasecurity.AwsConfig;
import uk.acmelabs.datasecurity.api.CMK;
import uk.acmelabs.datasecurity.aws.api.model.DataKey;

public class KMS {

  private final KmsAsyncClient awsClient;
  private final Executor defaultExecutor;

  public KMS(
    final Role role,
    final AwsConfig config
  ) {
    this.awsClient =
      KmsAsyncClient
        .builder()
        .credentialsProvider(config.awsCredentialsProvider(role))
        .endpointOverride(config.kmsEndpoint())
        .region(config.awsRegion())
        .asyncConfiguration(config.awsAsyncConfig())
        .overrideConfiguration(config.awsClientConfig())
        .build();
    this.defaultExecutor = config.defaultExecutor();
  }

  public CompletableFuture<CMK> createCMK() {
    return awsClient
      .createKey()
      .thenApplyAsync(
        response -> new CMK(response.keyMetadata()),
        defaultExecutor
      );
  }

  public CompletableFuture<DataKey> generateDataKey(final CMK cmk) {
    final GenerateDataKeyRequest request =
      GenerateDataKeyRequest
        .builder()
        .keyId(cmk.getId())
        .keySpec(DataKeySpec.AES_256)
        .build();

    return awsClient
            .generateDataKey(request)
            .thenApplyAsync(
              response -> new DataKey(response.ciphertextBlob(), response.plaintext()),
              defaultExecutor
            );
  }

  public CompletableFuture<DataKey> decryptDataKey(
    final CMK cmk,
    final byte[] dataKey
  ) {
    final SdkBytes encryptedDataKey = SdkBytes.fromByteArray(dataKey);
    final DecryptRequest request =
      DecryptRequest
        .builder()
        .keyId(cmk.getId())
        .ciphertextBlob(encryptedDataKey)
        .build();

    return awsClient
            .decrypt(request)
            .thenApplyAsync(response ->  new DataKey(encryptedDataKey, response.plaintext()),
              defaultExecutor
            );
  }
}
