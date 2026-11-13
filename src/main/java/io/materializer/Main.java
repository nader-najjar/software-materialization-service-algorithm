package io.materializer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.materializer.composition.EnvironmentModule;
import io.materializer.composition.GitHubClientsModule;
import io.materializer.composition.StrictGuiceModule;
import io.materializer.execution.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point.
 * The following classes are excluded from unit tests and require deployment smoke tests
 * supplied by the consuming application:
 *   - `Main.java`
 *   - `composition/*`
 */
public final class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final int SHUTDOWN_GRACE_PERIOD_SECONDS = 25;
    private static final CountDownLatch MAIN_FINISHED = new CountDownLatch(1);
    private static volatile boolean shutdownRequested;

    private Main() { }

    public static void main(String[] args) {
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopMain(mainThread), "shutdown-hook"));

        int exitCode = 0;
        Injector injector = null;
        try {
            injector = Guice.createInjector(
                    com.google.inject.Stage.PRODUCTION,
                    new StrictGuiceModule(),
                    new EnvironmentModule(),
                    new GitHubClientsModule()
            );
            Executor executor = injector.getInstance(Executor.class);
            executor.execute(args);
        } catch (Exception exception) {
            logFailure(exception);
            exitCode = 1;
        } finally {
            MAIN_FINISHED.countDown();
        }

        System.exit(exitCode);
    }

    private static void logFailure(Exception exception) {
        if (!shutdownRequested) {
            LOGGER.error("Technical exception occurred at software entrypoint level: ", exception);
        } else {
            LOGGER.info("Stopped by shutdown request: {}", exception.toString());
        }
    }

    private static void stopMain(Thread mainThread) {
        shutdownRequested = true;
        mainThread.interrupt();
        try {
            if (!MAIN_FINISHED.await(SHUTDOWN_GRACE_PERIOD_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn("Main did not finish within {}s; JVM will exit", SHUTDOWN_GRACE_PERIOD_SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Shutdown hook interrupted");
        }
    }
}
