# testy-box [![](https://img.shields.io/github/release/Marthym/testy-box.svg)](https://GitHub.com/Marthym/testy-box/releases/) [![GitHub license](https://img.shields.io/github/license/Marthym/testy-box.svg)](https://github.com/Marthym/testy-box/blob/master/LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Marthym_testy-box&metric=alert_status)](https://sonarcloud.io/dashboard?id=Marthym_testy-box)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Marthym_testy-box&metric=coverage)](https://sonarcloud.io/dashboard?id=Marthym_testy-box)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Marthym_testy-box&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=Marthym_testy-box)


## Description

`testy-box` is a module providing many extensions for **JUnit 5** tests:

* **testy-beat-box** provides extensions to run an in-memory [Qpid](https://qpid.apache.org/) AMQP broker and provide reactive RabbitMQ connections.
* **[testy-core-box](testy-core-box/README.md)** provides core extensions. All the other projects depend on it.
* **[testy-jooq-box](testy-jooq-box/README.md)** provides extensions to run an in-memory H2 database. Test data can be inserted using [JOOQ](https://www.jooq.org/).
* **[testy-mongo-box](testy-mongo-box/README.md)** provides extensions to run an embedded [MongoDB](https://www.mongodb.com/) database. Test data can be inserted.
* **[testy-params-box](testy-params-box/README.md)** provides aggregators for ParameterizedTest.
* **[testy-redis-box](testy-redis-box/README.md)** provides extensions to run an embedded [Redis](https://redis.io/) database.
* **[testy-search-box](testy-search-box/README.md)** provides extensions to run an embedded [OpenSearch](https://opensearch.org/) node. Test data can be indexed.

## testy-core-box

This project provides common extensions

-> **[See module documentation](testy-core-box/README.md)**

## testy-jooq-box

This project is used to test SQL repositories.

-> **[See module documentation](testy-jooq-box/README.md)**

## testy-beat-box

This project is used to test classes using RabbitMQ. It provides an extension to run an embedded AMQP broker.

* [WithRabbitMock](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/beat/extensions/WithRabbitMock.html) runs an embedded AMQP broker.

### WithRabbitMock

This extension runs an embedded AMQP broker ([Qpid](https://qpid.apache.org/) by default).

When registered, the Rabbit [SenderOptions](https://projectreactor.io/docs/rabbitmq/snapshot/api/reactor/rabbitmq/SenderOptions.html) and [ReceiverOptions](https://projectreactor.io/docs/rabbitmq/milestone/api/reactor/rabbitmq/ReceiverOptions.html) can be injected as parameters.

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .build();

@BeforeAll
static void beforeClass(SenderOptions senderOptions,
                        ReceiverOptions receiverOptions) {
    // (...)
}
```

Before each test method, a RabbitMQ [Connection](https://www.rabbitmq.com/releases/rabbitmq-java-client/v1.4.0/rabbitmq-java-client-javadoc-1.4.0/com/rabbitmq/client/Connection.html) and a [Channel](https://www.rabbitmq.com/releases/rabbitmq-java-client/v1.4.0/rabbitmq-java-client-javadoc-1.4.0/com/rabbitmq/client/Channel.html) are opened. They can be injected as parameters.

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .build();

@BeforeEach
void setUp(Connection connection,
           Channel channel) {
    // (...)
}
```

Queues and exchanges can also be created with the extension. When declaring a queue and an exchange, they are bound together with an empty routing key.

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .declareQueueAndExchange("my_queue", "my_exchange")
            .build();
```

The queues are deleted automatically by closing the connection after each test method.

In order to simplify mocking of rabbit queues, Mocked sender and receiver can be injected to the test:

- [MockedSender](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/beat/messaging/MockedSender.html)
- [MockedReceiver](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/beat/messaging/MockedReceiver.html)

These mocks use [AmqpMessage](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/beat/messaging/AmqpMessage.html) as requests/responses. **AmqpMessage** can define the body and the headers of the message.

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .declareQueueAndExchange("my_queue", "my_exchange")
            .build();

@Test
void myTest(MockedSender mockedSender) {
    final AmqpMessage request = AmqpMessage.of("test-request".getBytes());
    final String routingKey = "";

    // Simply publish a message on the tested queue
    mockedSender.basicPublish(request)
                .on("my_exchange", routingKey);

    // Send a RPC request
    final Mono<Delivery> response = mockedSender.rpc(request)
                .on("my_exchange", routingKey);
    // (...)
}
```

```java
@RegisterExtension
static final WithRabbitMock withRabbitMock = WithRabbitMock
            .builder()
            .declareQueueAndExchange("my_queue", "my_exchange")
            .build();

@Test
void myTest(MockedReceiver mockedReceiver) {
    final AmqpMessage response = AmqpMessage.of("test-response".getBytes());

    // To consume more than one message, use consume(int)
    final Flux<Delivery> requests = mockedReceiver.consumeOne()
                  .on("my_queue")
                  .thenRespond(response)
                  .start();
    // (...)
}
```
