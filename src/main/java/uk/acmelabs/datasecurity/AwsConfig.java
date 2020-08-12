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
package uk.acmelabs.datasecurity;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientAsyncConfiguration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.metrics.LoggingMetricPublisher;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

public class AwsConfig {

  public AwsCredentialsProvider awsCredentialsProvider(final Role role) {
    final AssumeRoleRequest roleRequest =
      AssumeRoleRequest
        .builder()
        .durationSeconds(role.maxSessionDuration())
        .roleArn(role.arn())
        .build();
    final DefaultCredentialsProvider defaultProvider =
      DefaultCredentialsProvider.create();
    final StsAssumeRoleCredentialsProvider stsProvider =
      StsAssumeRoleCredentialsProvider
        .builder()
        .stsClient(
          StsClient
            .builder()
            .endpointOverride(stsEndpoint())
            .region(awsRegion())
            .build()
        )
        .refreshRequest(roleRequest)
        .build();

    return AwsCredentialsProviderChain.of(stsProvider, defaultProvider);
  }

  public ClientAsyncConfiguration awsAsyncConfig() {
    return
      ClientAsyncConfiguration
        .builder()
        .advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, awsExecutor())
        .build();
  }

  public ClientOverrideConfiguration awsClientConfig() {
    return
      ClientOverrideConfiguration
        .builder()
        .apiCallAttemptTimeout(Duration.of(500, ChronoUnit.MILLIS))
        .apiCallTimeout(Duration.of(10, ChronoUnit.SECONDS))
        .retryPolicy(awsClientRetryPolicy())
        .addMetricPublisher(awsMetricPublisher())
        .build();
  }

  public URI iamEndpoint() {
    return getEndpoint("IAM_ENDPOINT", "iam");
  }

  public URI stsEndpoint() {
    return getEndpoint("STS_ENDPOINT", "sts");
  }

  public URI kmsEndpoint() {
    return getEndpoint("KMS_ENDPOINT", "kms");
  }

  public Region awsRegion() {
    return Region.of(Optional.of(System.getProperty("AWS_REGION")).orElse("eu-west-1"));
  }

  private final Executor awsExec = Executors.newWorkStealingPool();
  public Executor awsExecutor() {
    return awsExec;
  }

  private final Executor defaultExec = Executors.newWorkStealingPool();
  public Executor defaultExecutor() {
    return defaultExec;
  }

  public RetryPolicy awsClientRetryPolicy() {
    final BackoffStrategy backoff =
      FullJitterBackoffStrategy
        .builder()
        .baseDelay(Duration.of(200, ChronoUnit.MILLIS))
        .maxBackoffTime(Duration.of(10, ChronoUnit.SECONDS))
        .build();

    return RetryPolicy
      .builder()
      .backoffStrategy(backoff)
      .numRetries(5)
      .build();
  }

  public MetricPublisher awsMetricPublisher() {
    return LoggingMetricPublisher.create();
  }

  private URI getEndpoint(String envVar, String service) {
    final String defaultUri = String.format("https://%s.%s.amazonaws.com", service, awsRegion());
    final String uri = System.getProperty(envVar);

    try {
      return new URI(Optional.of(uri).orElse(defaultUri));
    } catch (URISyntaxException error) {
      // Configuration error - so bring the application down hard
      throw new ConfigurationException(error.getMessage());
    }
  }
}
