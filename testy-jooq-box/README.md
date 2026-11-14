## testy-jooq-box

This project is used to test SQL repositories.
It provides extensions to load an in-memory H2 database, execute SQL scripts with [Flyway](https://flywaydb.org) and insert test data.

* [WithInMemoryDatasource](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/jooq/WithInMemoryDatasource.html) loads a H2 SQL database in-memory on a named catalog.
* [WithDatabaseLoaded](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/jooq/WithDatabaseLoaded.html) creates the database schema on the catalog using [Flyway](https://flywaydb.org) SQL scripts.
* [WithDslContext](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/jooq/WithDslContext.html) creates JOOQ `DSLContext` from the input DataSource.
* [WithSampleDataLoaded](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/jooq/WithSampleDataLoaded.html) reset the content of the tables before each test using JOOQ records.

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

If the test registers more than one data source, the parameters shall be annotated with `jakarta.inject.Named` to distinguish them:

```java
@RegisterExtension
static final WithInMemoryDatasource wDataSource1 = WithInMemoryDatasource
            .builder()
            .setCatalog("my_catalog_1")
            .build();

@RegisterExtension
static final WithInMemoryDatasource wDataSource2 = WithInMemoryDatasource
            .builder()
            .setCatalog("my_catalog_2")
            .build();

@BeforeEach
void setUp(@Named("my_catalog_1") DataSource dataSource1, 
           @Named("my_catalog_2") DataSource dataSource2) {
    // (...)
}
```

### WithDatabaseLoaded

This extension depends on a [DatasourceExtension](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/jooq/DatasourceExtension.html) and runs a [Flyway](https://flywaydb.org/) migration on the related DB catalog.

By default, the SQL scripts have to be located in `db/migration/<catalog>` in the classpath, where `<catalog>` is the name of DataSource catalog. For example, use `src/test/resources/db/migration/my_catalog/V1__create_schema.sql`. The names of the SQL files shall match Flyway's versioned migration naming convention: `V<version>__<description>.sql`.

The database schema is cleaned and the SQL scripts are run **once before all the test methods**. They are expected to be used to create the database schema.

```java
private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
        .builder()
        .setCatalog("my_catalog")
        .build();

// SQL files shall be located in classpath:db/migration/my_catalog
private static final WithDatabaseLoaded wDatabaseLoaded = WithDatabaseLoaded
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();

@RegisterExtension
static final ChainedExtension chain = ChainedExtension
        .outer(wDataSource)
        .append(wDatabaseLoaded)
        .register();
```

### WithDslContext

This extension depends on a [DatasourceExtension](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/jooq/DatasourceExtension.html) and creates a [JOOQ DSLContext](https://www.jooq.org/doc/3.13/manual/sql-building/dsl-context/) on the related DataSource.

```java
    private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
            .builder()
            .setCatalog("my_catalog")
            .build();
    private static final WithDslContext wDsl = WithDslContext
            .builder()
            .setDatasourceExtension(wDataSource)
            .build();

    @RegisterExtension
    static final ChainedExtension chain = ChainedExtension
            .outer(wDataSource)
            .append(wDsl)
            .register();
```

This DSL can be injected as parameter.

```java
@BeforeEach
void setUp(DSLContext dsl) {
    // (...)
}
```

If many catalogs are registered, the parameter shall be annotated with `jakarta.inject.Named`:

```java
@BeforeEach
void setUp(@Named("my_catalog_1") DSLContext dsl1, 
           @Named("my_catalog_2") DSLContext dsl2) {
    // (...)
}
```

### WithSampleDataLoaded

This extension deletes and inserts test data **before each test method**.

The test data are inserted as JOOQ records. They can be defined with classes implementing [RelationalDataSet](https://marthym.github.io/testy-box/fr/ght1pc9kc/testy/jooq/model/RelationalDataSet.html).
The following example uses `JediRecord` and the `JEDI` table generated from this module's test schema. In your project, use your own generated records and tables, and create the corresponding schema through the Flyway migrations.

```java
import fr.ght1pc9kc.testy.dsl.public_.tables.records.JediRecord;
import fr.ght1pc9kc.testy.jooq.model.RelationalDataSet;
import java.util.List;

import static fr.ght1pc9kc.testy.dsl.public_.tables.Jedi.JEDI;

public final class JediDataSet implements RelationalDataSet<JediRecord> {

    @Override
    public List<JediRecord> records() {
        return List.of(JEDI.newRecord()
                .setFirstName("Obiwan")
                .setLastName("Kenobi")
                .setForceSide("LIGHT"));
    }
}
```

These data set can be added to the extension `WithSampleDataLoaded` to setup the test data

```java
private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource
        .builder()
        .setCatalog("my_catalog")
        .build();
private static final WithDatabaseLoaded wDatabaseLoaded = WithDatabaseLoaded
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();
private static final WithDslContext wDSLContext = WithDslContext
        .builder()
        .setDatasourceExtension(wDataSource)
        .build();

private static final WithSampleDataLoaded wSamples = WithSampleDataLoaded
        .builder(wDSLContext)
        .addDataset(new JediDataSet())
        .build();

@RegisterExtension
static final ChainedExtension chain = ChainedExtension
        .outer(wDataSource)
        .append(wDatabaseLoaded)
        .append(wDSLContext)
        .append(wSamples)
        .register();
```

:fire: Only the tables related to the data sets are emptied before each test. If a test inserts rows into another table, this table shall be emptied manually. :fire:
