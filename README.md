# Data Security Library

Provides library code to allow data producers to use KMS CMKs to generate data keys for 
encrypting messages.

FIXME: say more!!

## Compiling

```shell script
./gradlew clean compileJava compileTestScala compileGatlingScala
```

## Unit and Integration Testing

Testing coverage reports:
```shell script
./build/reports/test/test/index.html
```

### Against localstack Docker Service

```shell script
./gradlew test
```

Docker logging will be found in:
```shell script
./build/docker-compose.log
```

### Against AWS STS and KMS Services

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

TODO:

## Library Configuration

### Data Producer Configuration

TODO:

### Data Consumer Configuration

TODO:

## Library API

### Data Producer API

```java
public DataProducer(final Function<Message, CompletableFuture<Void>> deliver, final ProducerConfig config)
```

```java
final public CompletableFuture<Void> send(final ByteBuffer data, final CMK cmk)
```

TODO: point out tests to look at

### Data Consumer API

```java
public DataConsumer(final Function<ByteBuffer, CompletableFuture<Void>> processor, final ConsumerConfig config)
```

```java
final public CompletableFuture<Void> receive(final Message message)
```

TODO: point out tests to look at
