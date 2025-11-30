package fr.ght1pc9kc.testy.core.extensions;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Allow getting a Jackson ObjectMapper in Tests.
 * <p>
 * By default, this extension searches and registers modules in the classpath, but it is possible
 * to pass a specific module list at creation time.
 * </p>
 * <p>
 * The {@link DateTimeFeature#WRITE_DATES_AS_TIMESTAMPS} feature is disable, only no null
 * properties were included in serialization.
 * </p>
 */
public class WithJsonMapper implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {
    private static final String P_JACKSON_MAPPER = "jackson-mapper";

    private final Set<JacksonModule> modules = new HashSet<>();
    private final Map<Class<?>, Class<?>> mixins = new HashMap<>();
    private final boolean findAndRegisterModules;

    public WithJsonMapper() {
        this.findAndRegisterModules = true;
    }

    private WithJsonMapper(boolean findAndRegisterModules) {
        this.findAndRegisterModules = findAndRegisterModules;
    }

    public static WithJsonMapperBuilder builder() {
        return new WithJsonMapperBuilder();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        JsonMapper.Builder builder = JsonMapper.builder();
        if (findAndRegisterModules) {
            builder.findAndAddModules();
        }

        builder.configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .changeDefaultPropertyInclusion(spec -> spec.withContentInclusion(JsonInclude.Include.NON_NULL))
                .addModules(modules);

        mixins.forEach(builder::addMixIn);

        Store store = getStore(context);
        store.put(P_JACKSON_MAPPER, builder.build());
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Store store = getStore(context);
        Object mapper = store.get(P_JACKSON_MAPPER);
        if (mapper == null) {
            beforeAll(context);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return JsonMapper.class.equals(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        if (JsonMapper.class.equals(parameterContext.getParameter().getType())) {
            return getStore(extensionContext).get(P_JACKSON_MAPPER);
        }
        throw new ParameterResolutionException("Unable to resolve ObjectMapper !");
    }

    public JsonMapper getObjectMapper(ExtensionContext context) {
        return getStore(context).get(P_JACKSON_MAPPER, JsonMapper.class);
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass()));
    }

    /**
     * Allow to build a more complex ObjectMapper with {@link Module} and Mixin
     * <p>
     * Usage :
     * <pre style="code">
     *     {@literal @}RegisterExtension
     *     WithJsonMapper wMapper = WithJsonMapper.builder()
     *             .dontFindAndRegisterModules()
     *             .addMixin(Dummy.class, DummyMixin.class)
     *             .build();
     * </pre>
     */
    public static class WithJsonMapperBuilder {
        private final Set<JacksonModule> modules = new HashSet<>();
        private final Map<Class<?>, Class<?>> mixins = new HashMap<>();
        private boolean findAndRegisterModules = true;

        /**
         * Avoid looking for Modules in classpath and register there
         *
         * @return the builder
         */
        public WithJsonMapperBuilder dontFindAndRegisterModules() {
            findAndRegisterModules = false;
            return this;
        }

        /**
         * Register specific {@link Module} to ObjectMapper
         *
         * @param module The Module to register
         * @return the builder
         */
        public WithJsonMapperBuilder addModule(JacksonModule module) {
            this.modules.add(module);
            return this;
        }

        /**
         * Register multiple {@link JacksonModule}s at the same time
         *
         * @param modules The collection of Modules to register
         * @return the builder
         */
        public WithJsonMapperBuilder addModules(Collection<JacksonModule> modules) {
            this.modules.addAll(modules);
            return this;
        }

        /**
         * Register Mixin to the {@link ObjectMapper}
         *
         * @param entityClass The entity class
         * @param mixinClass  The Mixin class
         * @return the builder
         */
        public WithJsonMapperBuilder addMixin(Class<?> entityClass, Class<?> mixinClass) {
            this.mixins.put(entityClass, mixinClass);
            return this;
        }

        /**
         * Build the Object Mapper junit extension
         *
         * @return The extension
         */
        public WithJsonMapper build() {
            WithJsonMapper withObjectMapper = new WithJsonMapper(findAndRegisterModules);
            withObjectMapper.modules.addAll(this.modules);
            withObjectMapper.mixins.putAll(this.mixins);
            return withObjectMapper;
        }
    }
}
