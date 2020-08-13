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

import java.nio.ByteBuffer
import java.util.concurrent.{CompletableFuture, Executors}

import scala.collection.mutable
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import net.logstash.logback.argument.StructuredArguments.keyValue
import org.junit.runner.RunWith
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.slf4j.{Logger, LoggerFactory}
import software.amazon.awssdk.services.iam.model.Role
import uk.acmelabs.datasecurity.api.{ByteBufferGen, Message}
import uk.acmelabs.datasecurity.consumer.{ConsumerConfig, DataConsumer}
import uk.acmelabs.datasecurity.producer.{DataProducer, ProducerConfig}

@RunWith(classOf[JUnitRunner])
class SingleConsumerEndToEndIT
  extends AnyFreeSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ScalaFutures {

  import ByteBufferGen._

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool())

  val iamSetup = new IAMSetup(new AwsConfig())

  val logger: Logger = LoggerFactory.getLogger("SingleConsumerEndToEndIT")

  val timeout: Timeout = Timeout(1.minute)

  val producerConfig: Future[ProducerConfig] = {
    for {
      roleEncrypt <- iamSetup.encryptRole
    } yield {
      new ProducerConfig {
        override def encryptRole(): Role = roleEncrypt
      }
    }
  }

  val consumerConfig: Future[ConsumerConfig] = {
    for {
      roleDecrypt <- iamSetup.decryptRole
    } yield {
      new ConsumerConfig {
        override def decryptRole(): Role = roleDecrypt
      }
    }
  }

  def producer(probe: mutable.ListBuffer[Message]): Future[DataProducer] = {
    for {
      config <- producerConfig
    } yield {
      // localstack does not care about authentication, so we don't bother using encryptUser credentials here
      new DataProducer((msg: Message) =>
        CompletableFuture.runAsync(new Runnable {
          def run(): Unit = {
            logger.info("Producer.deliver", keyValue("Message", msg.toMap))
            probe += msg
          }
        }),
        config
      )
    }
  }

  def consumer(
                dataProbe: mutable.ListBuffer[ByteBuffer],
                copyProbe: mutable.ListBuffer[ByteBuffer]
              ): Future[DataConsumer] = {
    for {
      config <- consumerConfig
    } yield {
      // localstack does not care about authentication, so we don't bother using decryptUser credentials here
      new DataConsumer((data: ByteBuffer) =>
        CompletableFuture.runAsync(new Runnable {
          def run(): Unit = {
            assert(data.isReadOnly)

            val dataCopy = (0 until data.capacity()).map { index =>
              data.get(index)
            }.toArray[Byte]
            val dataStr = dataCopy.map("%02X" format _).mkString

            if (data.capacity() == 0) {
              logger.info("Consumer.processor", keyValue("ByteBuffer", "empty"))
            } else {
              logger.info("Consumer.processor", keyValue("ByteBuffer", s"0x$dataStr"))
            }

            dataProbe += data
            copyProbe += ByteBuffer.wrap(dataCopy).asReadOnlyBuffer()
          }
        }),
        config
      )
    }
  }

  "Roundtrip encrypting/decrypting of plaintext" in {
    forAll(genByteBuffer) { data =>
      val producerProbe = mutable.ListBuffer.empty[Message]
      val consumerOriginalProbe = mutable.ListBuffer.empty[ByteBuffer]
      val consumerCopyProbe = mutable.ListBuffer.empty[ByteBuffer]
      val sendFuture: Future[Unit] =
        for {
          key <- iamSetup.cmk
          client <- producer(producerProbe)
          _ <- client.send(data.asReadOnlyBuffer(), key).toScala
        } yield ()
      val receiveObservable: Message => Future[Unit] =
        secureMessage =>
          for {
            client <- consumer(consumerOriginalProbe, consumerCopyProbe)
            _ <- client.receive(secureMessage).toScala
          } yield ()

      whenReady(sendFuture, timeout) { _ =>
        producerProbe should have length 1
      }

      whenReady(receiveObservable(producerProbe.head), timeout) { _ =>
        consumerOriginalProbe should have length 1
        consumerCopyProbe should have length 1

        val plaintextOriginalData = consumerOriginalProbe.head
        val plaintextCopyData = consumerCopyProbe.head

        (0 until plaintextOriginalData.capacity()).foreach { index =>
          plaintextOriginalData.get(index) shouldEqual 0.toByte
        }
        data shouldEqual plaintextCopyData
      }
    }
  }
}
