package fr.ght1pc9kc.testy.search;

import fr.ght1pc9kc.testy.core.extensions.ChainedExtension;
import fr.ght1pc9kc.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.search.Hit;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WithOpenSearchDataTest {
    private static final String INDEX_0 = "first-index";
    private static final String INDEX_1 = "second-index";
    private static final String INDEX_JEDI = "jedi";

    record Jedi(String name, String side) {
    }

    private static final OpenSearchDataSet<Map<String, Object>> MAP_DATA_SET = () -> List.of(
            Map.of("_id", "DOC0", "title", "Test document 0"),
            Map.of("_id", "DOC1", "title", "Test document 1"));

    private static final OpenSearchDataSet<Jedi> JEDI_DATA_SET = new OpenSearchDataSet<>() {
        @Override
        public List<Jedi> documents() {
            return List.of(new Jedi("Luke", "light"), new Jedi("Obiwan", "light"));
        }

        @Override
        public String identifier() {
            return "name";
        }
    };

    private static final WithEmbeddedOpenSearch W_OPENSEARCH = WithEmbeddedOpenSearch.builder().build();
    private static final WithObjectMapper W_OBJECT_MAPPER = WithObjectMapper.builder().build();
    private static final WithOpenSearchData W_OPENSEARCH_DATA = WithOpenSearchData.builder(W_OPENSEARCH)
            .withObjectMapper(W_OBJECT_MAPPER)
            .addDataset(INDEX_0, MAP_DATA_SET)
            .addDataset(INDEX_1, MAP_DATA_SET)
            .addDataset(INDEX_JEDI, JEDI_DATA_SET)
            .build();

    @RegisterExtension
    @SuppressWarnings("unused")
    static final ChainedExtension chain = ChainedExtension.outer(W_OPENSEARCH)
            .append(W_OBJECT_MAPPER)
            .append(W_OPENSEARCH_DATA)
            .register();

    private OpenSearchClient client;

    @BeforeEach
    void setUp(OpenSearchClient client) {
        this.client = client;
    }

    @Test
    @Order(1)
    void should_have_indexed_map_data() throws IOException {
        for (String index : List.of(INDEX_0, INDEX_1)) {
            assertThat(hits(index))
                    .containsOnlyKeys("DOC0", "DOC1")
                    .containsEntry("DOC0", Map.of("title", "Test document 0"));
        }
    }

    @Test
    @Order(2)
    void should_have_indexed_clazz_data(WithOpenSearchData.Tracker tracker) throws IOException {
        tracker.skipNextSampleLoad();

        assertThat(hits(INDEX_JEDI)).containsOnlyKeys("Luke", "Obiwan")
                .containsEntry("Luke", Map.of("name", "Luke", "side", "light"));

        client.index(i -> i.index(INDEX_JEDI).id("Darth").document(Map.of("name", "Vader")).refresh(Refresh.True));
    }

    @Test
    @Order(3)
    void should_skip_reset_data() throws IOException {
        assertThat(hits(INDEX_JEDI)).containsOnlyKeys("Luke", "Obiwan", "Darth");
    }

    @Test
    @Order(4)
    void should_reset_data_after_skip() throws IOException {
        assertThat(hits(INDEX_JEDI)).containsOnlyKeys("Luke", "Obiwan");
    }

    @SuppressWarnings("rawtypes")
    private Map<String, Map> hits(String index) throws IOException {
        return client.search(s -> s.index(index), Map.class).hits().hits().stream()
                .collect(Collectors.toMap(Hit::id, Hit::source));
    }
}
