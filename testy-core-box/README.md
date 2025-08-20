## testy-core-box

Testy-Core6box is the basic module with few dependencies. It provides the following tools :

* [WithObjectMapper](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/core/extensions/WithObjectMapper.html) configures a [Jackson](https://github.com/FasterXML/jackson) mapper for Java to JSON conversion.
* [ChainedExtension](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/core/extensions/ChainedExtension.html) registers other test extensions and initializes them in the order of the declaration.

### WithObjectMapper

This extension creates and stores an `ObjectMapper` at step `BeforeAll`. This mapper can be injected as parameter.

```java
@RegisterExtension
static final WithObjectMapper wObjectMapper = WithObjectMapper
            .builder()
            .addMixin(MyModel.class, MyModelMixin.class)
            .addModule(new ParameterNamesModule())
            .addModule(new JavaTimeModule())
            .build();

@BeforeAll
static void beforeClass(ObjectMapper objectMapper) {
    // (...)
}
```

### ChainedExtension

This extension registers other extensions and runs them:

* `BeforeEach` and `BeforeAll` callbacks are run in the order of the declaration.
* `AfterEach` and `AfterAll` callbacks are run in the reverse order of the declaration.
* `ParameterResolver` resolves a type with the first extension able to resolve it. If none can resolve a parameter, the parameter resolution will fail with standard JUnit exception.

This extension is usefull to register test resources in order (for instance, register the DataSource before loading the database schema):


```java
private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
        .builder()
        .setTraceLevel(DatabaseTraceLevel.ERROR)
        .setCatalog("my_db_catalog")
        .build();

private static final WithDatabaseLoaded wTestDatabase = WithDatabaseLoaded
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();

@RegisterExtension
static final ChainedExtension chain = ChainedExtension
        .outer(wDataSource)
        .append(wTestDatabase)
        .register();
```

### GlobalThreadMonitorExtension

When working on highly competitive projects, juggling between executors and schedulers, it can happen that you forget 
to properly close the thread pools opened for testing.

This quickly loads the stack and, in problematic cases, could lead to errors such as:

```
Surefire is going to kill self fork JVM.
The exit has elapsed 30 seconds after System.exit(0).
```

This extension allows you to prevent the problem by recording it on your risky tests. But it will also allow you to 
detect problematic tests among your hundreds of tests by recording them globally.

#### Register as a class extension

```java
@ExtendWith(GlobalThreadMonitorExtension.class)
class NestedExtensionTest {
    @Test
    void should_complete_test_with_non_daemon_thread_running() {
        executors.submit(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // ignore
            }
        });

        Assertions.assertThat(executors.isShutdown()).isFalse();
    }
}
```

When running this test, you will see the following output:

```shell
[WARNING] ⚠️ Threads non-daemon après BaywatchApplicationTests
          -> global-thread-monitor-1
```

#### Register as a global extension

If the problem is more serious and you don't know which test the problem is in, GlobalThreadMonitorExtension 
automatically registers itself globally via serviceLoader. So, you just need to run the tests with the option :

`-Djunit.jupiter.extensions.autodetection.enabled=true`

so that the extension applies to all tests and allows you to identify the source of the problem.