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

import java.util.Base64

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.iam.model._
import uk.acmelabs.datasecurity.AwsConfig

class IAMUtil(val config: AwsConfig)(implicit ec: ExecutionContext) {

  private val iam = {
    IamAsyncClient
      .builder()
      .region(config.awsRegion())
      .endpointOverride(config.iamEndpoint())
      .asyncConfiguration(config.awsAsyncConfig())
      .overrideConfiguration(config.awsClientConfig())
      .build()
  }

  object IAMUser {
    def create(name: String): Future[User] = {
      val request =
        CreateUserRequest
          .builder()
          .userName(name)
          .build()

      for {
        response <- iam.createUser(request).toScala
      } yield response.user()
    }

    def attachPolicy(name: String, policy: Policy): Future[Unit] = {
      val request =
        AttachUserPolicyRequest
          .builder()
          .policyArn(policy.arn())
          .userName(name)
          .build()

      for {
        _ <- iam.attachUserPolicy(request).toScala
      } yield ()
    }

    def listPolicies(name: String): Future[List[String]] = {
      val request =
        ListUserPoliciesRequest
          .builder()
          .userName(name)
          .build()

      for {
        response <- iam.listUserPolicies(request).toScala
      } yield response.policyNames().asScala.toList
    }

    def credentials(name: String): Future[AccessKey] = {
      val request =
        CreateAccessKeyRequest
          .builder()
          .userName(name)
          .build()

      for {
        response <- iam.createAccessKey(request).toScala
      } yield response.accessKey()
    }

    def delete(name: String): Future[Unit] = {
      val request =
        DeleteUserRequest
          .builder()
          .userName(name)
          .build()

      for {
        _ <- iam.deleteUser(request).toScala
      } yield ()
    }

    def get(name: String): Future[User] = {
      val request =
        GetUserRequest
          .builder()
          .userName(name)
          .build()

      for {
        response <- iam.getUser(request).toScala
      } yield response.user()
    }

    def list(): Future[List[User]] = {
      for {
        response <- iam.listUsers().toScala
      } yield response.users().asScala.toList
    }
  }

  object IAMPolicy {
    def create(name: String, document: String): Future[Policy] = {
      val request =
        CreatePolicyRequest
          .builder()
          .policyName(name)
          .policyDocument(document)
          .build()

      for {
        response <- iam.createPolicy(request).toScala
      } yield response.policy()
    }

    def list(): Future[List[Policy]] = {
      for {
        response <- iam.listPolicies().toScala
      } yield response.policies().asScala.toList
    }
  }

  object IAMRole {
    def create(name: String, rolePolicy: String): Future[Role] = {
      val request =
        CreateRoleRequest
          .builder()
          .roleName(name)
          .assumeRolePolicyDocument(rolePolicy)
          .build()

      for {
        response <- iam.createRole(request).toScala
      } yield response.role()
    }

    def attachPolicy(policy: Policy, role: Role): Future[Unit] = {
      val request =
        AttachRolePolicyRequest
          .builder()
          .roleName(role.roleName())
          .policyArn(policy.arn())
          .build()

      for {
        _ <- iam.attachRolePolicy(request).toScala
      } yield ()
    }

    def listPolicies(name: String): Future[List[String]] = {
      val request =
        ListRolePoliciesRequest
          .builder()
          .roleName(name)
          .build()

      for {
        response <- iam.listRolePolicies(request).toScala
      } yield response.policyNames().asScala.toList
    }

    def delete(name: String): Future[Unit] = {
      val request =
        DeleteRoleRequest
          .builder()
          .roleName(name)
          .build()

      for {
        _ <- iam.deleteRole(request).toScala
      } yield ()
    }

    def get(name: String): Future[Role] = {
      val request =
        GetRoleRequest
          .builder()
          .roleName(name)
          .build()

      for {
        response <- iam.getRole(request).toScala
      } yield response.role()
    }

    def list(): Future[List[Role]] = {
      for {
        response <- iam.listRoles().toScala
      } yield response.roles().asScala.toList
    }
  }

  object IAMReport {
    def credentials(): Future[Array[Byte]] = {
      for {
        _ <- iam.generateCredentialReport().toScala
        report <- iam.getCredentialReport().toScala
      } yield Base64.getDecoder.decode(report.content().asByteArray())
    }

    def accesses(): Future[List[AccessDetail]] = {
      val request =
        GenerateOrganizationsAccessReportRequest
          .builder()
          .build()

      for {
        response <- iam.generateOrganizationsAccessReport(request).toScala
        request = GetOrganizationsAccessReportRequest.builder().jobId(response.jobId()).build()
        report <- iam.getOrganizationsAccessReport(request).toScala
      } yield report.accessDetails().asScala.toList
    }

    final case class AuthorizationDetails(
      userDetails: List[UserDetail],
      roleDetails: List[RoleDetail],
      groupDetails: List[GroupDetail],
      policies: List[ManagedPolicyDetail]
    )

    def authorizationDetails(): Future[AuthorizationDetails] = {
      for {
        response <- iam.getAccountAuthorizationDetails().toScala
      } yield {
        AuthorizationDetails(
          response.userDetailList().asScala.toList,
          response.roleDetailList().asScala.toList,
          response.groupDetailList().asScala.toList,
          response.policies().asScala.toList
        )
      }
    }
  }
}
