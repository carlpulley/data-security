# Data Security Library

Provides library code to allow data producers to use KMS CMKs to generate data keys for 
encrypting messages.

## Compiling

```shell script
./gradlew clean compileJava
```

## Unit Testing and localstack Integration Testing

```shell script
./gradlew test
```

Unit testing reports are in:
```shell script
./buid/reports/tests/test/index.html
```

Docker logging will be found in:
```shell script
./build/docker-compose.log
```

and test coverage reports are in:
```shell script
./build/reports/jacoco/index.html
```

### Changing AWS STS and KMS Endpoints

```shell script
export AWS_REGION=us-east-1
export STS_ENDPOINT=https://sts.$AWS_REGION.amazonaws.com
export KMS_ENDPOINT=https://kms.$AWS_REGION.amazonaws.com

./gradlew test -DAWS_REGION=${AWS_REGION} -DSTS_ENDPOINT=${STS_ENDPOINT} -DKMS_ENDPOINT=${KMS_ENDPOINT}
```

## Gatling Load Testing

### Run all Gatling Load Tests

```shell script
export NUMBER_USERS=10
export SIMULATION_DURATION=30

./gradlew gatlingRun -DnumberOfUsers=${NUMBER_USERS} -DsimulationDuration=${SIMULATION_DURATION}
```

TODO:

### Run a Specific Gatling Load Test

```shell script
export SIMULATION=EncryptDataTestSimulation
export NUMBER_USERS=10
export SIMULATION_DURATION=30

./gradlew gatlingRun-${SIMULATION} -DnumberOfUsers=${NUMBER_USERS} -DsimulationDuration=${SIMULATION_DURATION}
```

TODO:

### Setting up AWS STS and KMS Services for all Gatling Load Tests

```shell script
export AWS_REGION=us-east-1
export STS_ENDPOINT=https://sts.$AWS_REGION.amazonaws.com
export KMS_ENDPOINT=https://kms.$AWS_REGION.amazonaws.com

export NUMBER_USERS=10
export SIMULATION_DURATION=30

./gradlew gatlingRun -DAWS_REGION=${AWS_REGION} -DSTS_ENDPOINT=${STS_ENDPOINT} -DKMS_ENDPOINT=${KMS_ENDPOINT} -DnumberOfUsers=${NUMBER_USERS} -DsimulationDuration=${SIMULATION_DURATION}
```

TODO:

### Setting up AWS STS and KMS Services for a Specific Gatling Load Test

```shell script
export AWS_REGION=us-east-1
export STS_ENDPOINT=https://sts.$AWS_REGION.amazonaws.com
export KMS_ENDPOINT=https://kms.$AWS_REGION.amazonaws.com

export SIMULATION=EncryptDataTestSimulation
export NUMBER_USERS=10
export SIMULATION_DURATION=30

./gradlew gatlingRun-${SIMULATION} -DAWS_REGION=${AWS_REGION} -DSTS_ENDPOINT=${STS_ENDPOINT} -DKMS_ENDPOINT=${KMS_ENDPOINT} -DnumberOfUsers=${NUMBER_USERS} -DsimulationDuration=${SIMULATION_DURATION}
```

## Deployable Artifacts

Deployment jar file is found in:
```shell script
./build/libs/
```
with name matching:
```shell script
data-security-${VERSION}.jar
```

Manifest for this jar file contains useful build information such as:
* Library-Name
* Library-Version
* Build-hostname
* Build-user
* Git-version
* Git-branch
* Git-commit-hash
* Docker-version

## Library Configuration

### AWS Configuration

`uk.acmelabs.datasecurity.AwsConfig` manages all common AWS configuration. This includes:
* AWS credentials providers for asynchronous clients
* retry and backoff strategies for asynchronous clients
* executors to use for asynchronous clients
* configurable AWS client metric producer definition
* AWS service endpoints and regions.

### Data Producer Configuration

`uk.acmelabs.datasecurity.producer.ProducerConfig` manages all data-security producer configuration - including the AWS
role producer will assume.

### Data Consumer Configuration

`uk.acmelabs.datasecurity.consumer.ConsumerConfig` manages all data-security consumer configuration - including the AWS
role consumer will assume.

## Library API

### Data Producer API

When creating a data-security producer instance, the following constructor function is called:
```java
final public DataProducer(final Function<Message, CompletableFuture<Void>> deliver, final ProducerConfig config)
```
The `deliver` parameter is an asynchronous function that will handle the mechanics of message delivery.

When the producer wishes to send a message (specified by the parameter `data`), that will be encrypted using a data key 
under the KMS CMK `cmk`, then the following producer method is called:
```java
final public CompletableFuture<Void> send(final ByteBuffer data, final CMK cmk)
```
If the message is successfully sent, then the Java future will resolve correctly. Should the message send fail, then the 
Java future will resolve with an exception.

### Data Consumer API

When creating a data-security consumer instance, the following constructor function is called:
```java
final public DataConsumer(final Function<ByteBuffer, CompletableFuture<Void>> processor, final ConsumerConfig config)
```
The `processor` parameter is an asynchronous function that will handle the mechanics of processing a received plaintext 
message.

When the consumer is ready to decrypt a received message (specified by the parameter `message`), then the following 
consumer method is called:
```java
final public CompletableFuture<Void> receive(final Message message)
```
If the message is successfully decrypted and processed, then the Java future will resolve correctly. Should the message 
decryption or processing fail, then the Java future will resolve with an exception.
