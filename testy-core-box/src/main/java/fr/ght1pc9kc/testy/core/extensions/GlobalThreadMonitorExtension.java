package fr.ght1pc9kc.testy.core.extensions;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.util.List;

@SuppressWarnings("java:S106")
public class GlobalThreadMonitorExtension implements AfterAllCallback {
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final Logger log = LoggerFactory.getLogger(GlobalThreadMonitorExtension.class);

    @Override
    public void afterAll(ExtensionContext context) {
        log.debug(() -> "## GlobalThreadMonitor ####################################### start ##");
        List<Thread> nonDaemons = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> !t.isDaemon() && !"main".equals(t.getName()))
                .toList();

        if (!nonDaemons.isEmpty()) {
            System.err.printf("[%sWARNING%s] ⚠️ non-daemon threads after %s%n", ANSI_YELLOW, ANSI_RESET, context.getDisplayName());
            nonDaemons.forEach(t -> System.err.printf("          -> %s%n", t.getName()));
        }
        log.debug(() -> "## GlobalThreadMonitor #######################################   end ##");
    }
}