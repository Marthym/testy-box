package fr.ght1pc9kc.testy.search;

import fr.ght1pc9kc.testy.core.utils.PortUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpHost;
import org.codelibs.opensearch.runner.OpenSearchRunner;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import java.lang.reflect.Parameter;
import java.util.UUID;

/**
 * Starts a single-node OpenSearch cluster inside the test JVM, without Docker.
 * <p>
 * Uses <strong>opensearch-runner</strong> from <strong>codelibs</strong>. OpenSearch has no in-memory store,
 * so the node writes its data in a temporary directory that is deleted once the tests are done.
 * </p>
 * <p>This extension allows the injection of:</p>
 * <ul>
 *     <li><code>{@literal @}OpenSearchPort Integer port</code>: the HTTP port of the node</li>
 *     <li><code>OpenSearchClient client</code>: an <strong>opensearch-java</strong> client connected to the node</li>
 *     <li><code>OpenSearchRunner runner</code>: the runner itself</li>
 * </ul>
 *
 * <h2>Usage :</h2>
 * <h3>With <code>{@literal @}ExtendWith</code></h3>
 * <pre>{@code
 *  @ExtendWith(WithEmbeddedOpenSearch.class)
 *  class WithEmbeddedOpenSearchTest {
 *      @Test
 *      void should_use_embedded_opensearch(OpenSearchClient client) throws IOException {
 *          client.index(i -> i.index("my-index").id("1").document(Map.of("name", "Luke")));
 *      }
 *  }
 * }</pre>
 *
 * <h3>With <code>{@literal @}RegisterExtension</code></h3>
 * <pre>{@code
 * @RegisterExtension
 * static WithEmbeddedOpenSearch wOpenSearch = WithEmbeddedOpenSearch.builder().build();
 * }</pre>
 *
 * @see <a href="https://github.com/codelibs/opensearch-runner">codelibs/opensearch-runner</a>
 * @see <a href="https://github.com/opensearch-project/opensearch-java">opensearch-java</a>
 */
@Slf4j
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithEmbeddedOpenSearch implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(WithEmbeddedOpenSearch.class);

    private static final String P_OPENSEARCH_PORT = "openSearchPort";
    private static final String P_OPENSEARCH_CLIENT = "openSearchClient";
    private static final String P_OPENSEARCH_RUNNER = "openSearchRunner";

    @Builder.Default
    private final int httpPort = PortUtils.randomFreePort();

    @Builder.Default
    private final String clusterName = "testy-" + UUID.randomUUID();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        OpenSearchRunner runner = new OpenSearchRunner();
        runner.onBuild((index, settings) -> settings
                        .put("http.port", httpPort)
                        .put("transport.port", PortUtils.randomFreePort()))
                .build(OpenSearchRunner.newConfigs()
                        .clusterName(clusterName)
                        .numOfNode(1)
                        .useLogger()
                        .disableESLogger());
        runner.ensureYellow();

        OpenSearchClient client = new OpenSearchClient(ApacheHttpClient5TransportBuilder
                .builder(new HttpHost("http", "localhost", httpPort))
                .build());
        log.atDebug().addArgument(httpPort).setMessage("Embedded OpenSearch started on port {}").log();

        ExtensionContext.Store store = getStore(context);
        store.put(P_OPENSEARCH_PORT, httpPort);
        store.put(P_OPENSEARCH_CLIENT, client);
        store.put(P_OPENSEARCH_RUNNER, runner);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = getStore(context);

        OpenSearchClient client = store.get(P_OPENSEARCH_CLIENT, OpenSearchClient.class);
        if (client != null) {
            client._transport().close();
        }

        OpenSearchRunner runner = store.get(P_OPENSEARCH_RUNNER, OpenSearchRunner.class);
        if (runner != null) {
            runner.close();
            runner.clean();
            log.atDebug().log("Embedded OpenSearch stopped");
        }
    }

    public OpenSearchClient getClient(ExtensionContext context) {
        return getStore(context).get(P_OPENSEARCH_CLIENT, OpenSearchClient.class);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        return OpenSearchClient.class.equals(type)
                || OpenSearchRunner.class.equals(type)
                || (Integer.class.equals(type) && parameter.isAnnotationPresent(OpenSearchPort.class));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        if (OpenSearchClient.class.equals(type)) {
            return getStore(extensionContext).get(P_OPENSEARCH_CLIENT);
        } else if (OpenSearchRunner.class.equals(type)) {
            return getStore(extensionContext).get(P_OPENSEARCH_RUNNER);
        } else if (Integer.class.equals(type) && parameter.isAnnotationPresent(OpenSearchPort.class)) {
            return getStore(extensionContext).get(P_OPENSEARCH_PORT);
        }

        throw new ParameterResolutionException(getClass().getName() + " must be static and package-protected !");
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(NAMESPACE);
    }
}
