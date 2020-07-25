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
package uk.acmelabs.datasecurity.util

import java.net.URI
import java.util.concurrent.Executor

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsCredentialsProvider, AwsCredentialsProviderChain, StaticCredentialsProvider}
import software.amazon.awssdk.core.client.config.{ClientAsyncConfiguration, ClientOverrideConfiguration}
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.metrics.MetricPublisher
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.model.{Role, User}
import uk.acmelabs.datasecurity.AwsConfig
import uk.acmelabs.datasecurity.api.CMK
import uk.acmelabs.datasecurity.aws.api.model.DataKey
import uk.acmelabs.datasecurity.aws.client.KMS

final class KMSUtil(val iamUtil: IAMUtil)(implicit user: User, role: Role, ec: ExecutionContext) {

  import iamUtil._

  private def kms: Future[KMS] = {
    for {
      userCredentials <- IAMUser.credentials(user.userName())
      userProvider =
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(userCredentials.accessKeyId(), userCredentials.secretAccessKey())
        )
    } yield {
      val config = new AwsConfig {
        override def awsCredentialsProvider(role: Role): AwsCredentialsProvider = {
          AwsCredentialsProviderChain.of(userProvider, super.awsCredentialsProvider(role))
        }

        override def awsAsyncConfig(): ClientAsyncConfiguration = iamUtil.config.awsAsyncConfig()

        override def awsClientConfig(): ClientOverrideConfiguration = iamUtil.config.awsClientConfig()

        override def iamEndpoint(): URI = iamUtil.config.iamEndpoint()

        override def stsEndpoint(): URI = iamUtil.config.stsEndpoint()

        override def kmsEndpoint(): URI = iamUtil.config.kmsEndpoint()

        override def awsRegion(): Region = iamUtil.config.awsRegion()

        override def awsExecutor(): Executor = iamUtil.config.awsExecutor()

        override def defaultExecutor(): Executor = iamUtil.config.defaultExecutor()

        override def awsClientRetryPolicy(): RetryPolicy = iamUtil.config.awsClientRetryPolicy()

        override def awsMetricPublisher(): MetricPublisher = iamUtil.config.awsMetricPublisher()
      }

      new KMS(role, config)
    }
  }

  object CustomerMasterKey {
    def create(): Future[CMK] = {
      for {
        client <- kms
        cmk <- client.createCMK().toScala
      } yield cmk
    }
  }

  object DataEncryptionKey {
    def create(cmk: CMK): Future[DataKey] = {
      for {
        client <- kms
        dataKey <- client.generateDataKey(cmk).toScala
      } yield dataKey
    }
  }
}
