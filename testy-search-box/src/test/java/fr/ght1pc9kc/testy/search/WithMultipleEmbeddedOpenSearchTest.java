package fr.ght1pc9kc.testy.search;

import fr.ght1pc9kc.testy.core.extensions.ChainedExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WithMultipleEmbeddedOpenSearchTest {
    private static final WithEmbeddedOpenSearch FIRST = WithEmbeddedOpenSearch.builder().build();
    private static final WithEmbeddedOpenSearch SECOND = WithEmbeddedOpenSearch.builder().build();

    @RegisterExtension
    static final ChainedExtension chain = ChainedExtension.outer(FIRST).append(SECOND).register();

    private ExtensionContext context;

    @RegisterExtension
    final BeforeEachCallback captureContext = context -> this.context = context;

    @Test
    void should_keep_cluster_data_separate() throws IOException {
        OpenSearchClient firstClient = FIRST.getClient(context);
        OpenSearchClient secondClient = SECOND.getClient(context);

        assertThat(firstClient).isNotSameAs(secondClient);

        firstClient.index(i -> i.index("first-only").id("1").document(Map.of("name", "Luke")));

        assertThat(firstClient.indices().exists(i -> i.index("first-only")).value()).isTrue();
        assertThat(secondClient.indices().exists(i -> i.index("first-only")).value()).isFalse();
    }
}
