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
package uk.acmelabs.datasecurity

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

import software.amazon.awssdk.services.iam.model.{Role, User}
import uk.acmelabs.datasecurity.api.CMK
import uk.acmelabs.datasecurity.aws.api.model.DataKey
import uk.acmelabs.datasecurity.util.{IAMUtil, KMSUtil}

final class IAMSetup(config: AwsConfig)(implicit ec: ExecutionContext) extends IAMUtil(config)(ec) {

  private val awaitTimeout: FiniteDuration = 30.seconds

  val kmsAdminUser: User = {
    Await.result(IAMUser.create("kms-admin-user"), awaitTimeout)
  }

  val kmsAdminRole: Role = {
    val accessPolicy =
      """{
        |  "Version": "2012-10-17",
        |  "Statement": [
        |    {
        |      "Action": [
        |        "kms:CreateKey",
        |        "kms:ListGrants",
        |        "kms:ListRetirableGrants"
        |      ],
        |      "Effect": "Allow",
        |      "Resource": "arn:aws:kms:::*"
        |    }
        |  ]
        |}
        |""".stripMargin
    val result = for {
      role <- IAMRole.create("KMSAdmin", trustPolicy(kmsAdminUser))
      _ <- addAccessPolicy("kms-admin-policy", accessPolicy, role)
    } yield role

    Await.result(result, awaitTimeout)
  }

  private val kmsUtil = new KMSUtil(this)(kmsAdminUser, kmsAdminRole, ec)

  import kmsUtil._

  val encryptUser: User = {
    Await.result(IAMUser.create("encrypt-user"), awaitTimeout)
  }

  val decryptUser: User = {
    Await.result(IAMUser.create("decrypt-user"), awaitTimeout)
  }

  val encryptRole: Role = {
    val accessPolicy =
      """{
        |  "Version": "2012-10-17",
        |  "Statement": [
        |    {
        |      "Effect": "Allow",
        |      "Action": [
        |        "kms:GenerateDataKey",
        |        "kms:CreateGrant",
        |        "kms:RevokeGrant"
        |      ],
        |      "Resource": "arn:aws:kms:::*"
        |    }
        |  ]
        |}
        |""".stripMargin
    val result =
      for {
        role <- IAMRole.create("PIIEncrypt", trustPolicy(encryptUser))
        _ <- addAccessPolicy("encrypt-policy", accessPolicy, role)
      } yield role

    Await.result(result, awaitTimeout)
  }

  val decryptRole: Role = {
    val accessPolicy =
      """{
        |  "Version": "2012-10-17",
        |  "Statement": [
        |    {
        |      "Effect": "Allow",
        |      "Action": [
        |        "kms:Decrypt"
        |      ],
        |      "Resource": "arn:aws:kms:::*"
        |    }
        |  ]
        |}
        |""".stripMargin
    val result =
      for {
        role <- IAMRole.create("PIIDecrypt", trustPolicy(decryptUser))
        _ <- addAccessPolicy("decrypt-policy", accessPolicy, role)
      } yield role

      Await.result(result, awaitTimeout)
  }

  val cmk: CMK = {
    Await.result(CustomerMasterKey.create(), awaitTimeout)
  }

  val cmkList: Seq[CMK] = {
    val result =
      Future.sequence {
        (0 until 10).map(_ => CustomerMasterKey.create())
      }

    Await.result(result, awaitTimeout)
  }

  val kmsMap: Map[CMK, Seq[DataKey]] = {
    val result =
      for {
        entries <- Future.sequence(cmkList.map(dataEntry))
      } yield Map(entries: _*)

    Await.result(result, awaitTimeout)
  }

  private def dataKey(cmk: CMK): Future[DataKey] =
    DataEncryptionKey.create(cmk)

  private def dataEntry(cmk: CMK): Future[(CMK, Seq[DataKey])] =
    for {
      dataKeys <- Future.sequence[DataKey, Seq]((0 until 5).map(_ => dataKey(cmk)))
    } yield (cmk, dataKeys)

  private def trustPolicy(user: User): String = {
    s"""{
       |  "Version": "2012-10-17",
       |  "Statement": [
       |    {
       |      "Effect": "Allow",
       |      "Action": [
       |        "sts:AssumeRole"
       |      ],
       |      "Principal": {
       |        "AWS": "${user.arn()}"
       |      }
       |    }
       |  ]
       |}
       |""".stripMargin
  }

  private def addAccessPolicy(name: String, accessPolicy: String, role: Role): Future[Unit] = {
    for {
      policy <- IAMPolicy.create(name, accessPolicy)
      _ <- IAMRole.attachPolicy(policy, role)
    } yield ()
  }
}
