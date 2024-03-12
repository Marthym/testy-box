package fr.ght1pc9kc.testy.mongo;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ght1pc9kc.testy.core.extensions.WithObjectMapper;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;

import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extension allowing to initialize a mongo database with data.
 * <p>
 * Example of use with model:
 * <pre>
 *
 * &#64;Builder
 * &#64;Value
 * public class User {
 *
 *     private final String id;
 *     private final String login;
 *     private final String firstName;
 *     private final String lastName;
 *     private final String password;
 *
 * }
 * </pre>
 * <p>
 * Implementation of the data set:
 * <pre>
 *
 * public class UserDataSet implements MongoDataSet&lt;User&gt; {
 *     &#64;Override
 *     public List&lt;User&gt; documents() {
 *         final User user = User.builder()
 *                 .id("generated-id")
 *                 .firstName("Obiwan")
 *                 .lastName("Kenobi")
 *                 .login("okenobi")
 *                 .password("110812f67fa1e1f0117f6f3d70241c1a42a7b07711a93c2477cc516d9042f9db")
 *                 .build();
 *         return ImmutableList.of(user);
 *     }
 * }
 * </pre>
 * <p>
 * Use of the extension. Before each test, the mongo test database will contain the documents of the data set:
 * <pre>
 * public class UserMongoRepositoryTest {
 *
 *     private static final String USER_COLLECTION = "user";
 *
 *     private static final WithEmbeddedMongo WITH_EMBEDDED_MONGO = WithEmbeddedMongo.builder()
 *             .build();
 *     private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
 *             .addMixin(User.class, UserMongoMixin.class)
 *             .build();
 *     private static final WithMongoData WITH_MONGO_DATA = WithMongoData.builder(WITH_EMBEDDED_MONGO)
 *             .withObjectMapper(WITH_OBJECT_MAPPER)
 *             .addDataset(USER_COLLECTION, new UserDataSet())
 *             .build();
 *
 *     &#64;RegisterExtension
 *     static final ChainedExtension CHAIN = ChainedExtension.outer(WITH_EMBEDDED_MONGO)
 *             .append(WITH_OBJECT_MAPPER)
 *             .append(WITH_MONGO_DATA)
 *             .register();
 *
 *     // (...)
 * }
 * </pre>
 *
 * <h2>Database Tracker</h2>
 * <p>For performance reasons, the lib provides a {@link Tracker} that lets the extension know that the database has
 * not been modified, and that it is therefore not necessary to recreate all the collections.</p>
 *
 * <pre><code>
 * {@literal @}Test
 *  void should_read_data(WithMongoData.Tracker tracker) {
 *      tracker.skipNextSampleLoad();
 *      ...
 *  }
 * </code></pre>
 */
public final class WithMongoData implements BeforeEachCallback, BeforeAllCallback, ParameterResolver {
    private static final String MONGO_ID_FIELD = "_id";
    private static final String P_TRACKER = "sampleTracker_";

    private final WithEmbeddedMongo wEmbeddedMongo;
    private final @Nullable WithObjectMapper wObjectMapper;
    private final Map<String, MongoDataSet<?>> dataSets;

    private WithMongoData(WithEmbeddedMongo wEmbeddedMongo,
                          Map<String, MongoDataSet<?>> dataSets) {
        this.wEmbeddedMongo = wEmbeddedMongo;
        this.wObjectMapper = null;
        this.dataSets = dataSets;
    }

    private WithMongoData(WithEmbeddedMongo wEmbeddedMongo,
                          @NotNull WithObjectMapper wObjectMapper,
                          Map<String, MongoDataSet<?>> dataSets) {
        this.wEmbeddedMongo = wEmbeddedMongo;
        this.wObjectMapper = wObjectMapper;
        this.dataSets = dataSets;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        String dbName = wEmbeddedMongo.getDatabaseName();
        getStore(context).put(P_TRACKER + dbName, new Tracker());
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        final ObjectMapper objectMapper = Optional.ofNullable(this.wObjectMapper)
                .map(wom -> wom.getObjectMapper(context))
                .orElseGet(ObjectMapper::new);
        final ReactiveMongoTemplate mongoTemplate = this.wEmbeddedMongo.getMongoTemplate(context);
        final Tracker tracker = getStore(context).get(P_TRACKER + this.wEmbeddedMongo.getDatabaseName(), Tracker.class);

        if (tracker == null) {
            throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
        } else if (tracker.skipNext.getAndSet(false)) {
            return;
        }

        dataSets.forEach((collection, dataSet) -> {
            mongoTemplate.dropCollection(collection).block();
            fillCollection(mongoTemplate, objectMapper, collection, dataSet);
        });
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
        Class<?> type = parameterContext.getParameter().getType();
        final String dbName = wEmbeddedMongo.getDatabaseName();
        return Tracker.class.equals(type) && dbName.equals(getCatalogForParameter(parameterContext, dbName));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        final String dbName = wEmbeddedMongo.getDatabaseName();
        if (Tracker.class.equals(type)) {
            return getStore(extensionContext).get(P_TRACKER + dbName);
        }

        throw new NoSuchElementException(P_TRACKER + dbName);
    }

    private String getCatalogForParameter(ParameterContext parameterContext, String dbName) {
        return parameterContext.findAnnotation(Named.class)
                .map(Named::value)
                .orElse(dbName);
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        String dbName = wEmbeddedMongo.getDatabaseName();
        return context.getStore(ExtensionContext.Namespace.create(getClass().getName(), dbName));
    }

    private void fillCollection(ReactiveMongoTemplate mongoDb, ObjectMapper objectMapper, String collectionName, MongoDataSet<?> dataSet) {
        final List<Document> toInsert = dataSet.documents().stream()
                .map(o -> {
                    if (o instanceof Document document) {
                        return document;
                    }
                    Document doc = objectMapper.convertValue(o, Document.class);
                    Object identifier = doc.get(dataSet.identifier());
                    if (identifier != null) {
                        doc.remove(dataSet.identifier());
                        doc.put(MONGO_ID_FIELD, identifier);
                    }
                    return doc;
                }).toList();

        mongoDb.insertAll(Mono.just(toInsert), collectionName).blockLast();
    }

    /**
     * Create a {@link WithMongoDataBuilder} for the extension.
     *
     * @param wEmbeddedMongo Embedded mongo database.
     * @return {@link WithMongoDataBuilder}.
     */
    public static WithMongoDataBuilder builder(WithEmbeddedMongo wEmbeddedMongo) {
        return new WithMongoDataBuilder(wEmbeddedMongo);
    }

    /**
     * Builder for the extension class {@link WithMongoData}.
     */
    public static final class WithMongoDataBuilder {

        private final WithEmbeddedMongo wEmbeddedMongo;
        @Nullable
        private WithObjectMapper wObjectMapper;
        private final Map<String, MongoDataSet<?>> dataSetsBuilder = new HashMap<>();

        private WithMongoDataBuilder(WithEmbeddedMongo wEmbeddedMongo) {
            this.wEmbeddedMongo = wEmbeddedMongo;
        }

        /**
         * Add a customized object mapper to convert the objects to documents.
         *
         * @param wObjectMapper The {@link WithObjectMapper} extension to map the documents.
         * @return Builder instance.
         */
        public WithMongoDataBuilder withObjectMapper(WithObjectMapper wObjectMapper) {
            this.wObjectMapper = wObjectMapper;
            return this;
        }

        /**
         * Add a data set.
         *
         * @param collectionName Name of the collection the data-set will fill.
         * @param dataSet        Set of data to initialize the collection with.
         * @return Builder instance.
         */
        public WithMongoDataBuilder addDataset(String collectionName, MongoDataSet<?> dataSet) {
            this.dataSetsBuilder.put(collectionName, dataSet);
            return this;
        }

        /**
         * Build the extension.
         *
         * @return The built {@link WithMongoData} extension.
         */
        public WithMongoData build() {
            return Optional.ofNullable(wObjectMapper)
                    .map(wom -> new WithMongoData(wEmbeddedMongo, wom, Map.copyOf(dataSetsBuilder)))
                    .orElseGet(() -> new WithMongoData(wEmbeddedMongo, Map.copyOf(dataSetsBuilder)));
        }
    }

    public static class Tracker {
        private final AtomicBoolean skipNext = new AtomicBoolean(false);

        public void skipNextSampleLoad() {
            skipNext.set(true);
        }
    }
}
