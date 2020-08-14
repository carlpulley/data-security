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
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._

import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.validation._
import io.gatling.core.Predef.{global => validate, _}
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.feeder.Feeder
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.{ScenarioBuilder, ScenarioContext}
import software.amazon.awssdk.services.iam.model.Role
import uk.acmelabs.datasecurity.api._
import uk.acmelabs.datasecurity.consumer.{ConsumerConfig, DataConsumer}
import uk.acmelabs.datasecurity.producer.{DataProducer, ProducerConfig}
import uk.acmelabs.datasecurity.{IAMSetup, TestSetup}

final case class ValidationException(error: String*) extends Exception

object KMSClient {

  import ByteBufferGen._
  import MessageGen._

  val iamSetup: IAMSetup = TestSetup.iamSetup
  val encryptActionFeeder: Feeder[Any] = {
    val cmkGen = genCMK(iamSetup.kmsMap)

    Iterator.continually(
      for {
        actualData <- genByteBuffer.sample
        cmk <- cmkGen.sample
      } yield {
        Map(
          "actualData" -> actualData,
          "cmk" -> cmk
        )
      }
    ).collect {
      case Some(data) =>
        data
    }
  }
  val decryptActionFeeder: Feeder[Any] = {
    val messageGen = genMessage(iamSetup.kmsMap)

    Iterator.continually(
      for {
        (message, data) <- messageGen.sample
      } yield {
        Map(
          "message" -> message,
          "actualData" -> data,
          "cmk" -> message.getCMK
        )
      }
    ).collect {
      case Some(data) =>
        data
    }
  }
  val encryptData: EncryptActionBuilder = {
    new EncryptActionBuilder(
      "Encrypt Data",
      new ProducerConfig {
        override def encryptRole(): Role = iamSetup.encryptRole
      }
    )
  }
  val decryptData: DecryptActionBuilder = {
    new DecryptActionBuilder(
      "Decrypt Data",
      new ConsumerConfig {
        override def decryptRole(): Role = iamSetup.decryptRole
      }
    )
  }
}

class EncryptActionBuilder(name: String, config: ProducerConfig) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new EncryptAction(name, ctx.coreComponents.statsEngine, config, next)
}

class DecryptActionBuilder(name: String, config: ConsumerConfig) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new DecryptAction(name, ctx.coreComponents.statsEngine, config, next)
}

class EncryptAction(
                     val name: String,
                     statsEngine: StatsEngine,
                     config: ProducerConfig,
                     next: Action
                   ) extends Action {

  override def execute(session: Session): Unit = {
    val start = System.currentTimeMillis
    val actualData = session("actualData").validate[ByteBuffer]
    val cmk = session("cmk").validate[CMK]
    val producer =
      new DataProducer(
        (message: Message) => CompletableFuture.completedFuture[Void] {
          next ! session.set("message", message)
          null
        },
        config
      )
    val result: Future[Status] =
      (actualData, cmk) match {
        case (Success(data), Success(key)) =>
          producer.send(data.value, key.value).toScala.transformWith {
            case util.Success(_) =>
              Future {
                val end = System.currentTimeMillis
                statsEngine.logResponse(session, name, start, end, OK, None, None)
                OK
              }
            case util.Failure(error) =>
              Future {
                val end = System.currentTimeMillis
                statsEngine.logResponse(session, name, start, end, KO, None, Some(error.getMessage))
                next ! session
                KO
              }
          }
        case (Failure(error), Success(_)) =>
          Future.failed(ValidationException(error))
        case (Success(_), Failure(error)) =>
          Future.failed(ValidationException(error))
        case (Failure(error1), Failure(error2)) =>
          Future.failed(ValidationException(error1, error2))
      }

    result.onComplete {
      case util.Success(_) =>
        // No work to do!
      case util.Failure(error) =>
        statsEngine.logCrash(session, name, error.getMessage)
    }
  }
}

class DecryptAction(
                     val name: String,
                     statsEngine: StatsEngine,
                     config: ConsumerConfig,
                     next: Action
                   ) extends Action {

  override def execute(session: Session): Unit = {
    val start = System.currentTimeMillis
    val message = session("message").validate[Message]
    val actualData = session("actualData").validate[ByteBuffer]
    val consumer =
      new DataConsumer(
        (decryptedData: ByteBuffer) => actualData match {
          case Success(expectedData) =>
            CompletableFuture.completedFuture[Void] {
              next ! session.set("result", (decryptedData, expectedData.value))
              null
            }
          case Failure(error) =>
            CompletableFuture.failedFuture(ValidationException(error))
        },
        config
      )
    val result: Future[Status] = {
      message match {
        case Success(msg) =>
          consumer.receive(msg.value).toScala.transformWith {
            case util.Success(_) =>
              Future {
                val end = System.currentTimeMillis
                statsEngine.logResponse(session, name, start, end, OK, None, None)
                OK
              }
            case util.Failure(error) =>
              Future {
                val end = System.currentTimeMillis
                statsEngine.logResponse(session, name, start, end, KO, None, Some(error.getMessage))
                next ! session
                KO
              }
          }
        case Failure(error) =>
          Future.failed(ValidationException(error))
      }
    }

    result.onComplete {
      case util.Success(_) =>
        // No work to do!
      case util.Failure(error) =>
        statsEngine.logCrash(session, name, error.getMessage)
    }
  }
}

object Scenario {
  val encryptOnly: ScenarioBuilder =
    scenario("Encrypt Only")
      .feed(KMSClient.encryptActionFeeder)
      .exec(KMSClient.encryptData)

  val decryptOnly: ScenarioBuilder =
    scenario("Decrypt Only")
      .feed(KMSClient.decryptActionFeeder)
      .exec(KMSClient.decryptData)

  val encryptAndDecrypt: ScenarioBuilder =
    scenario("Encrypt and Decrypt")
      .feed(KMSClient.encryptActionFeeder)
      .exec(KMSClient.encryptData)
      .exec(KMSClient.decryptData)
      .exec(session => {
        session("result")
          .validate[(ByteBuffer, ByteBuffer)]
          .flatMap {
            case (actual, expected) if actual == expected =>
              Success(session)
            case _ =>
              Failure("data != decrypt(encrypt(data))")
          }
      })
}

trait KMSSimulation extends Simulation {
  val numberOfUsers: Int = Integer.getInteger("numberOfUsers", 350)
  val simulationDuration: FiniteDuration = Integer.getInteger("simulationDuration", 60).seconds
  val expectedSLA: Seq[Assertion] = Seq(
    validate.successfulRequests.percent.gte(98),
    validate.requestsPerSec.gte(10),
    validate.responseTime.percentile1.lte(100),
    validate.responseTime.percentile2.lte(250),
    validate.responseTime.percentile3.lte(300),
    validate.responseTime.percentile4.lte(350),
    validate.responseTime.max.lte(1000)
  )
}
