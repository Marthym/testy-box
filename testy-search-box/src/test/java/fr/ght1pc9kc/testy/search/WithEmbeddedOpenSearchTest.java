package fr.ght1pc9kc.testy.search;

import org.codelibs.opensearch.runner.OpenSearchRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetResponse;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(WithEmbeddedOpenSearch.class)
class WithEmbeddedOpenSearchTest {
    @Test
    @SuppressWarnings("rawtypes")
    void should_use_embedded_opensearch(@OpenSearchPort Integer port, OpenSearchClient client, OpenSearchRunner runner) throws IOException {
        assertThat(port).isPositive();
        assertThat(runner).isNotNull();

        client.index(i -> i.index("jedi").id("luke").document(Map.of("name", "Skywalker")));
        GetResponse<Map> actual = client.get(g -> g.index("jedi").id("luke"), Map.class);

        assertThat(actual.found()).isTrue();
        assertThat(actual.source()).containsEntry("name", "Skywalker");
    }
}
