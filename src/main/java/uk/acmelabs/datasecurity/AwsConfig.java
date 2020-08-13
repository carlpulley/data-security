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
import java.util.UUID;
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
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import uk.acmelabs.datasecurity.aws.metrics.LoggingMetricPublisher;

public class AwsConfig {

  /**
   * AWS credentials provider for assuming roles via STS. Credentials provider fails over to using the AWS default
   * credentials provider if STS fails to assume roles.
   *
   * @param role AWS role that is to be assumed
   * @return AWS credentials provider
   */
  public AwsCredentialsProvider awsCredentialsProvider(final Role role) {
    final AssumeRoleRequest roleRequest =
      AssumeRoleRequest
        .builder()
        .durationSeconds(role.maxSessionDuration())
        .roleArn(role.arn())
        .roleSessionName(UUID.randomUUID().toString())
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

  /**
   * AWS asynchronous configuration.
   *
   * @return AWS asynchronous configuration
   */
  public ClientAsyncConfiguration awsAsyncConfig() {
    return
      ClientAsyncConfiguration
        .builder()
        .advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, awsExecutor())
        .build();
  }

  /**
   * AWS HTTP client configuration.
   *
   * @return AWS client configuration overrides
   */
  public ClientOverrideConfiguration awsClientConfig() {
    return
      ClientOverrideConfiguration
        .builder()
        .apiCallAttemptTimeout(Duration.of(50, ChronoUnit.MILLIS))
        .apiCallTimeout(Duration.of(1, ChronoUnit.SECONDS))
        .retryPolicy(awsClientRetryPolicy())
        .addMetricPublisher(awsMetricPublisher())
        .build();
  }

  /**
   * AWS IAM endpoint to be used in client endpoint overrides.
   *
   * @return IAM URI
   */
  public URI iamEndpoint() {
    return getEndpoint("IAM_ENDPOINT", "iam");
  }

  /**
   * AWS STS endpoint to be used in client endpoint overrides.
   *
   * @return STS URI
   */
  public URI stsEndpoint() {
    return getEndpoint("STS_ENDPOINT", "sts");
  }

  /**
   * AWS KMS endpoint to be used in client endpoint overrides.
   *
   * @return KMS URI
   */
  public URI kmsEndpoint() {
    return getEndpoint("KMS_ENDPOINT", "kms");
  }

  /**
   * AWS region we operate in.
   *
   * @return AWS region
   */
  public Region awsRegion() {
    return Region.of(Optional.of(System.getProperty("AWS_REGION")).orElse("eu-west-1"));
  }

  private final Executor awsExec = Executors.newWorkStealingPool();

  /**
   * Defines the executor that AWS synchronous clients will use.
   *
   * @return executor to be used by AWS asynchronous clients
   */
  public Executor awsExecutor() {
    return awsExec;
  }

  private final Executor defaultExec = Executors.newWorkStealingPool();

  /**
   * Defines the executor that AWS service pipelines will use.
   *
   * @return executor to be used by AWS service pipelines
   */
  public Executor defaultExecutor() {
    return defaultExec;
  }

  /**
   * AWS retry policy for AWS asynchronous clients.
   *
   * @return retry policy
   */
  public RetryPolicy awsClientRetryPolicy() {
    final BackoffStrategy backoff =
      FullJitterBackoffStrategy
        .builder()
        .baseDelay(Duration.of(10, ChronoUnit.MILLIS))
        .maxBackoffTime(Duration.of(1, ChronoUnit.SECONDS))
        .build();

    return RetryPolicy
      .builder()
      .backoffStrategy(backoff)
      .numRetries(7)
      .build();
  }

  /**
   * Metric publisher for AWS asynchronous clients. By default, implemented as a logging publisher.
   *
   * @return metric publisher
   */
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
