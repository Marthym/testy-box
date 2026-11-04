package fr.ght1pc9kc.testy.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ght1pc9kc.testy.core.extensions.WithObjectMapper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extension resetting OpenSearch indices with data before each test.
 * <p>
 * Implementation of the data set:
 * <pre>
 * public class UserDataSet implements OpenSearchDataSet&lt;User&gt; {
 *     &#64;Override
 *     public List&lt;User&gt; documents() {
 *         return List.of(new User("okenobi", "Obiwan", "Kenobi"));
 *     }
 *
 *     &#64;Override
 *     public String identifier() {
 *         return "login";
 *     }
 * }
 * </pre>
 * <p>
 * Use of the extension. Before each test, the index is deleted and contains only the documents of the data set,
 * already refreshed and searchable:
 * <pre>
 * class UserSearchRepositoryTest {
 *     private static final WithEmbeddedOpenSearch W_OPENSEARCH = WithEmbeddedOpenSearch.builder().build();
 *     private static final WithObjectMapper W_OBJECT_MAPPER = WithObjectMapper.builder().build();
 *     private static final WithOpenSearchData W_OPENSEARCH_DATA = WithOpenSearchData.builder(W_OPENSEARCH)
 *             .withObjectMapper(W_OBJECT_MAPPER)
 *             .addDataset("user", new UserDataSet())
 *             .build();
 *
 *     &#64;RegisterExtension
 *     static final ChainedExtension CHAIN = ChainedExtension.outer(W_OPENSEARCH)
 *             .append(W_OBJECT_MAPPER)
 *             .append(W_OPENSEARCH_DATA)
 *             .register();
 * }
 * </pre>
 *
 * <h2>Data Tracker</h2>
 * <p>A test that does not modify the indices can inject the {@link Tracker} to skip the next reload.</p>
 *
 * <pre><code>
 * {@literal @}Test
 *  void should_read_data(WithOpenSearchData.Tracker tracker) {
 *      tracker.skipNextSampleLoad();
 *      ...
 *  }
 * </code></pre>
 */
public final class WithOpenSearchData implements BeforeEachCallback, BeforeAllCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(WithOpenSearchData.class);
    private static final String OPENSEARCH_ID_FIELD = "_id";
    private static final String P_TRACKER = "sampleTracker";
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final WithEmbeddedOpenSearch wOpenSearch;
    private final @Nullable WithObjectMapper wObjectMapper;
    private final Map<String, OpenSearchDataSet<?>> dataSets;

    private WithOpenSearchData(WithEmbeddedOpenSearch wOpenSearch,
                               @Nullable WithObjectMapper wObjectMapper,
                               Map<String, OpenSearchDataSet<?>> dataSets) {
        this.wOpenSearch = wOpenSearch;
        this.wObjectMapper = wObjectMapper;
        this.dataSets = dataSets;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getStore(NAMESPACE).put(P_TRACKER, new Tracker());
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        Tracker tracker = context.getStore(NAMESPACE).get(P_TRACKER, Tracker.class);
        if (tracker == null) {
            throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
        } else if (tracker.skipNext.getAndSet(false)) {
            return;
        }

        ObjectMapper objectMapper = Optional.ofNullable(wObjectMapper)
                .map(wom -> wom.getObjectMapper(context))
                .orElseGet(ObjectMapper::new);
        OpenSearchClient client = wOpenSearch.getClient(context);

        for (Map.Entry<String, OpenSearchDataSet<?>> entry : dataSets.entrySet()) {
            client.indices().delete(d -> d.index(entry.getKey()).ignoreUnavailable(true));
            fillIndex(client, objectMapper, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
        return Tracker.class.equals(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return Optional.ofNullable(extensionContext.getStore(NAMESPACE).get(P_TRACKER))
                .orElseThrow(() -> new NoSuchElementException(P_TRACKER));
    }

    private void fillIndex(OpenSearchClient client, ObjectMapper objectMapper, String index, OpenSearchDataSet<?> dataSet) throws IOException {
        List<BulkOperation> operations = dataSet.documents().stream()
                .map(o -> {
                    Map<String, Object> doc = objectMapper.convertValue(o, MAP_TYPE);
                    Object id = OPENSEARCH_ID_FIELD.equals(dataSet.identifier())
                            ? doc.remove(OPENSEARCH_ID_FIELD)
                            : doc.get(dataSet.identifier());
                    return BulkOperation.of(op -> op.index(i -> i
                            .id(Optional.ofNullable(id).map(String::valueOf).orElse(null))
                            .document(doc)));
                }).toList();
        if (operations.isEmpty()) {
            return;
        }

        BulkResponse response = client.bulk(b -> b.index(index).refresh(Refresh.True).operations(operations));
        if (response.errors()) {
            throw new UncheckedIOException(new IOException("Failed to index sample data in " + index + ": "
                    + response.items().stream()
                    .filter(item -> item.error() != null)
                    .map(item -> item.error().reason())
                    .toList()));
        }
    }

    /**
     * Create a {@link WithOpenSearchDataBuilder} for the extension.
     *
     * @param wOpenSearch Embedded OpenSearch node.
     * @return {@link WithOpenSearchDataBuilder}.
     */
    public static WithOpenSearchDataBuilder builder(WithEmbeddedOpenSearch wOpenSearch) {
        return new WithOpenSearchDataBuilder(wOpenSearch);
    }

    /**
     * Builder for the extension class {@link WithOpenSearchData}.
     */
    public static final class WithOpenSearchDataBuilder {
        private final WithEmbeddedOpenSearch wOpenSearch;
        private @Nullable WithObjectMapper wObjectMapper;
        private final Map<String, OpenSearchDataSet<?>> dataSets = new HashMap<>();

        private WithOpenSearchDataBuilder(WithEmbeddedOpenSearch wOpenSearch) {
            this.wOpenSearch = wOpenSearch;
        }

        /**
         * Add a customized object mapper to convert the objects to documents.
         *
         * @param wObjectMapper The {@link WithObjectMapper} extension to map the documents.
         * @return Builder instance.
         */
        public WithOpenSearchDataBuilder withObjectMapper(WithObjectMapper wObjectMapper) {
            this.wObjectMapper = wObjectMapper;
            return this;
        }

        /**
         * Add a data set.
         *
         * @param index   Name of the index the data set will fill.
         * @param dataSet Set of data to initialize the index with.
         * @return Builder instance.
         */
        public WithOpenSearchDataBuilder addDataset(String index, OpenSearchDataSet<?> dataSet) {
            this.dataSets.put(index, dataSet);
            return this;
        }

        /**
         * Build the extension.
         *
         * @return The built {@link WithOpenSearchData} extension.
         */
        public WithOpenSearchData build() {
            return new WithOpenSearchData(wOpenSearch, wObjectMapper, Map.copyOf(dataSets));
        }
    }

    public static class Tracker {
        private final AtomicBoolean skipNext = new AtomicBoolean(false);

        public void skipNextSampleLoad() {
            skipNext.set(true);
        }
    }
}
