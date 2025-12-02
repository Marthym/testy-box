package fr.ght1pc9kc.testy.core.extensions;

import fr.ght1pc9kc.testy.core.dummy.Dummy;
import fr.ght1pc9kc.testy.core.dummy.DummyMixin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("rawtypes")
class WithJsonMapperTest {
    static MockedStatic<MapperBuilder> utilities;

    @BeforeAll
    static void beforeAll() {
        utilities = Mockito.mockStatic(MapperBuilder.class);
        utilities.when(MapperBuilder::findModules).thenReturn(List.of(new AutoloadedModule()));
    }

    @AfterAll
    static void afterAll() {
        utilities.close();
    }

    @AfterEach
    void tearDown() {
        utilities.clearInvocations();
    }

    @Nested
    @ExtendWith(WithJsonMapper.class)
    @DisplayName("Test @ExtendWith(WithJsonMapper.class)")
    class WithJsonMapperTestSimple {
        @Test
        void should_inject_object_mapper(JsonMapper tested) {
            utilities.verify(MapperBuilder::findModules, Mockito.times(1));
            assertThat(tested.registeredModules()).extracting(JacksonModule::getModuleName).containsOnly(
                    "AutoloadedModule");
            assertThat(tested.serializationConfig().findMixInClassFor(Dummy.class)).isNull();
            assertThat(tested.deserializationConfig().findMixInClassFor(Dummy.class)).isNull();
        }
    }

    @Nested
    @DisplayName("Test @RegisterExtension WithJsonMapper")
    class WithJsonMapperTestComplex {
        @RegisterExtension
        static WithJsonMapper wMapper = WithJsonMapper.builder()
                .dontFindAndRegisterModules()
                .addModule(new DummyModule())
                .addMixin(Dummy.class, DummyMixin.class)
                .build();

        @Test
        void should_inject_object_mapper(JsonMapper tested) {
            utilities.verify(MapperBuilder::findModules, Mockito.never());
            assertThat(tested.registeredModules()).extracting(JacksonModule::getModuleName).containsOnly(
                    "DummyModule"
            );

            assertThat(tested.serializationConfig().findMixInClassFor(Dummy.class))
                    .isEqualTo(DummyMixin.class);
            assertThat(tested.deserializationConfig().findMixInClassFor(Dummy.class))
                    .isEqualTo(DummyMixin.class);
        }
    }

    @Nested
    @DisplayName("Test @RegisterExtension WithJsonMapper")
    class WithJsonMapperTestWithAutoload {
        @RegisterExtension
        static WithJsonMapper wMapper = WithJsonMapper.builder()
                .addModule(new DummyModule())
                .addMixin(Dummy.class, DummyMixin.class)
                .build();

        @Test
        void should_inject_object_mapper(ObjectMapper tested) {
            utilities.verify(MapperBuilder::findModules, Mockito.times(1));
            assertThat(tested.registeredModules()).extracting(JacksonModule::getModuleName).containsOnly(
                    "AutoloadedModule", "DummyModule"
            );

            assertThat(tested.serializationConfig().findMixInClassFor(Dummy.class))
                    .isEqualTo(DummyMixin.class);
            assertThat(tested.deserializationConfig().findMixInClassFor(Dummy.class))
                    .isEqualTo(DummyMixin.class);
        }
    }

    @Nested
    @DisplayName("Test @RegisterExtension WithJsonMapper")
    class WithJsonMapperTestWithNonStaticAutoload {
        @RegisterExtension
        @SuppressWarnings("JUnitMalformedDeclaration")
        WithJsonMapper wMapper = WithJsonMapper.builder()
                .addModule(new DummyModule())
                .addMixin(Dummy.class, DummyMixin.class)
                .build();

        @Test
        void should_inject_object_mapper(ObjectMapper tested) {
            utilities.verify(MapperBuilder::findModules, Mockito.times(1));
            assertThat(tested.registeredModules()).extracting(JacksonModule::getModuleName).containsOnly(
                    "AutoloadedModule", "DummyModule"
            );

            assertThat(tested.serializationConfig().findMixInClassFor(Dummy.class))
                    .isEqualTo(DummyMixin.class);
            assertThat(tested.deserializationConfig().findMixInClassFor(Dummy.class))
                    .isEqualTo(DummyMixin.class);
        }
    }

    @Nested
    @DisplayName("Test @RegisterExtension WithJsonMapper")
    class WithJsonMapperTestWithAddModules {
        @RegisterExtension
        @SuppressWarnings("JUnitMalformedDeclaration")
        WithJsonMapper wMapper = WithJsonMapper.builder()
                .addModules(List.of(new DummyModule()))
                .addMixin(Dummy.class, DummyMixin.class)
                .build();

        @Test
        void should_inject_object_mapper(ObjectMapper tested) {
            utilities.verify(MapperBuilder::findModules, Mockito.times(1));
            assertThat(tested.registeredModules()).extracting(JacksonModule::getModuleName).containsOnly(
                    "AutoloadedModule", "DummyModule"
            );

            assertThat(tested.serializationConfig().findMixInClassFor(Dummy.class))
                    .isEqualTo(DummyMixin.class);
            assertThat(tested.deserializationConfig().findMixInClassFor(Dummy.class))
                    .isEqualTo(DummyMixin.class);
        }
    }

    public static class DummyModule extends JacksonModule {
        @Override
        public String getModuleName() {
            return "DummyModule";
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        public void setupModule(SetupContext context) {
            // nothing to do
        }
    }

    public static class AutoloadedModule extends JacksonModule {
        @Override
        public String getModuleName() {
            return "AutoloadedModule";
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        public void setupModule(SetupContext context) {
            // nothing to do
        }
    }
}