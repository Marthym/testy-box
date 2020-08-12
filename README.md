# Testy-box

[[_TOC_]]

## Description

`Testy-box` is a module providing many extensions for **JUnit 5** tests:

* **testy-core-box** provides core extensions. All the other projects depend on it.
* **testy-jooq-box** provides extensions to run an in-memory H2 database. Test data can be inserted using [JOOQ](https://www.jooq.org/).
* **testy-mongo-box** provides extensions to run an in-memory [MongoDb](https://www.mongodb.com/) database. Test data can be inserted.
* **testy-beat-box** provides extensions to run an in-memory [Qpid](https://qpid.apache.org/) AMQP broker and provide reactive RabbitMQ connections.

## testy-core-box

This project provides common extensions:

* [WithObjectMapper](https://rocket.i-run.si/javadoc/fr/irun/testy/core/extensions/WithObjectMapper.html) configures a [Jackson](https://github.com/FasterXML/jackson) mapper for Java to JSON conversion.
* [ChainedExtension](https://rocket.i-run.si/javadoc/fr/irun/testy/core/extensions/ChainedExtension.html) registers other test extensions and initialize them in the order of the declaration.

### WithObjectMapper

This extension creates and store an `ObjectMapper` at step `BeforeAll`. This mapper can be injected as parameter in test, setUp and tearDown methods.

```java
@RegisterExtension
static final WithObjectMapper wObjectMapper = WithObjectMapper
            .builder()
            .addMixin(MyModel.class, MyModelMixin.class)
            .addModule(new ParameterNamesModule())
            .addModule(new GuavaModule())
            .addModule(new JavaTimeModule())
            .build();
```

### ChainedExtension

This extension registers other extensions and run them:

* `BeforeEach` and `BeforeAll` callbacks are run in the order of the declaration.
* `AfterEach` and `AfterAll` callbacks are run in the reverse order of the declaration.
* `ParameterResolver` resolves a type with the first extension able to resolve it. If none can resolve a parameter, the parameter resolution will fail with standard JUnit exception.

This extension is usefull to register test resources in order (for instance, register the DataSource before the database schema and the test data):


```java
private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
        .builder()
        .setTraceLevel(DatabaseTraceLevel.ERROR)
        .setCatalog("my_db_catalog")
        .build();

private static final WithDatabaseLoaded wIrunDatabase = WithDatabaseLoaded
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();

private static final WithDslContext wDSLContext = WithDslContext
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();

private static final WithSampleDataLoaded wSamples = WithSampleDataLoaded
        .builder(wDSLContext)
        .addDataset(new MyCustomDataSet())
        .build();

@RegisterExtension
static ChainedExtension chain = ChainedExtension
        .outer(wDataSource)
        .append(wIrunDatabase)
        .append(wDSLContext)
        .append(wSamples)
        .register();
```

## testy-jooq-box

This project provides extensions to load an in-memory H2 database with schema and test data for SQL repositories.

* [WithInMemoryDatasource](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/WithInMemoryDatasource.html) load a H2 SQL database in-memory on a named catalog.
* [WithDatabaseLoaded](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/WithDatabaseLoaded.html) creates the database schema on the catalog using [Flyway](https://flywaydb.org).
* [WithDslContext](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/WithDslContext.html) creates JOOQ `DSLContext` from the input DataSource.
* [WithSampleDataLoaded](https://rocket.i-run.si/javadoc/fr/irun/testy/jooq/WithSampleDataLoaded.html) reset the content of the tables before each test using JOOQ records.

### WithInMemoryDatasource

This extension creates a named H2 database in memory.

```java
@RegisterExtension
static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
            .builder()
            .setTraceLevel(DatabaseTraceLevel.ERROR)
            .setCatalog("my_catalog")
            .build();
```

After this extension has been registered, the `DataSource` can be injected as parameter.

```java
@BeforeAll
static void beforeClass(DataSource dataSource) {
    // (...)
}

@BeforeEach
void setUp(DataSource dataSource) {
    // (...)
}
```

If your test requires more than one data source, the parameters shall be annotated with `javax.inject.Named` to distinct them:

```java
@RegisterExtension
static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
            .builder()
            .setCatalog("my_catalog_1")
            .build();

@RegisterExtension
static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
            .builder()
            .setCatalog("my_catalog_2")
            .build();


@BeforeAll
static void beforeClass(@Named("my_catalog_1")
                        DataSource dataSource1) {
    // (...)
}

@BeforeEach
void setUp(@Named("my_catalog_2") 
           DataSource dataSource2) {
    // (...)
}
```