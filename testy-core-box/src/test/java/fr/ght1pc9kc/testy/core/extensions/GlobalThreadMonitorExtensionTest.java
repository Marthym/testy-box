package fr.ght1pc9kc.testy.core.extensions;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class GlobalThreadMonitorExtensionTest {
    private static final ExecutorService executors = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "global-thread-monitor"));
    private static ByteArrayOutputStream captureErr;
    private static PrintStream originalErr;

    @BeforeAll
    static void beforeAll() {
        captureErr = new ByteArrayOutputStream();
        originalErr = System.err;
        System.setErr(new PrintStream(captureErr));
    }

    @Nested
    @Order(1)
    @SuppressWarnings("java:S2925")
    @ExtendWith(GlobalThreadMonitorExtension.class)
    class NestedExtensionTest {
        @Test
        void should_complete_test_with_non_daemon_thread_running() {
            executors.submit(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // ignore
                }
            });

            Assertions.assertThat(executors.isShutdown()).isFalse();
        }
    }

    @Nested
    @Order(2)
    class VerifierTest {
        @Test
        void should_display_error_on_non_daemon_thread() {
            Assertions.assertThat(captureErr.toString()).contains("non-daemon thread");
        }
    }

    @AfterAll
    static void afterAll() {
        executors.shutdownNow();
        System.setErr(originalErr);
    }

}