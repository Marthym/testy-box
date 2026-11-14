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

The declared queues and exchanges use AMQP auto-delete mode. The reply queue is explicitly deleted after each test method, and the embedded broker is stopped after all tests.

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
    final Delivery reply = response.block(java.time.Duration.ofSeconds(1));
    // Verify the reply
}
```

The RPC request is sent when the returned `Mono` is subscribed to, for example with `block` as above. The tested consumer must be running and reply to the request.

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

`start()` immediately registers the consumer. After the tested code sends a request, subscribe to `requests` to inspect the received messages. The `Flux` completes once the expected number of requests has been consumed.
