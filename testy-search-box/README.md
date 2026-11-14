# testy-search-box

`testy-search-box` provides extensions to run an embedded [OpenSearch](https://opensearch.org/) node in your junit 5 tests.

The node runs inside the test JVM thanks to [opensearch-runner](https://github.com/codelibs/opensearch-runner): no Docker
required. OpenSearch has no in-memory store, so the data lives in a temporary directory deleted after the tests.

* `WithEmbeddedOpenSearch` starts a single-node OpenSearch cluster.
* `WithOpenSearchData` resets the content of indices before each test.

## WithEmbeddedOpenSearch

Through `@ExtendWith` annotation

```java
@ExtendWith(WithEmbeddedOpenSearch.class)
class WithEmbeddedOpenSearchTest {
    @Test
    void should_use_embedded_opensearch(@OpenSearchPort Integer port, OpenSearchClient client, OpenSearchRunner runner) {
        // your test code
    }
}
```

or through `@RegisterExtension`

```java
@RegisterExtension
static WithEmbeddedOpenSearch wOpenSearch = WithEmbeddedOpenSearch.builder()
        .httpPort(9250)
        .clusterName("my-cluster")
        .build();
```

`OpenSearchClient` is an [opensearch-java](https://github.com/opensearch-project/opensearch-java) client connected to the node.

## WithOpenSearchData

The documents of an index are defined by implementing `OpenSearchDataSet`. Objects are converted to JSON with Jackson.
The `identifier()` field (`_id` by default) gives the document id.

```java
record Jedi(String name, String side) {}

public class JediDataSet implements OpenSearchDataSet<Jedi> {
    @Override
    public List<Jedi> documents() {
        return List.of(new Jedi("Luke", "light"), new Jedi("Obiwan", "light"));
    }

    @Override
    public String identifier() {
        return "name";
    }
}
```

Before each test, each configured index is deleted and filled with its data set, already refreshed and searchable.

```java
private static final WithEmbeddedOpenSearch wOpenSearch = WithEmbeddedOpenSearch.builder().build();
private static final WithObjectMapper wObjectMapper = WithObjectMapper.builder().build();
private static final WithOpenSearchData wOpenSearchData = WithOpenSearchData.builder(wOpenSearch)
        .withObjectMapper(wObjectMapper)
        .addDataset("jedi", new JediDataSet())
        .build();

@RegisterExtension
static final ChainedExtension wExtensions = ChainedExtension.outer(wOpenSearch)
        .append(wObjectMapper)
        .append(wOpenSearchData)
        .register();
```

A test that does not modify the indices can skip the next reload:

```java
@Test
void should_read_data(WithOpenSearchData.Tracker tracker) {
    tracker.skipNextSampleLoad();
    // (...)
}
```
