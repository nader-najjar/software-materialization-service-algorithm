package io.materializer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import io.materializer.composition.GitHubClientsModule;
import io.materializer.composition.HttpClientModule;
import io.materializer.composition.MaterializationEnvironmentModule;
import io.materializer.composition.RegistrationEnvironmentModule;
import io.materializer.composition.SourceRegistrationEnvironmentModule;
import io.materializer.composition.StrictGuiceModule;
import io.materializer.execution.MaterializeAllSoftwareExecutor;
import io.materializer.execution.RegisterExecutor;
import io.materializer.execution.RegisterSourceExecutor;
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
        try {
            route(args);
        } catch (Exception exception) {
            logFailure(exception);
            exitCode = 1;
        } finally {
            MAIN_FINISHED.countDown();
        }

        System.exit(exitCode);
    }

    static void route(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Expected an operation");
        }
        if (args[0] == null) {
            throw new IllegalArgumentException("Operation must not be null");
        }
        switch (args[0]) {
            case "register" -> register(args);
            case "register-source" -> registerSource(args);
            case "materialize-all-software" -> materializeAllSoftware(args);
            default -> throw new IllegalArgumentException("Unsupported operation: " + args[0]);
        }
    }

    private static void register(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Register requires a JSON input");
        }
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                new StrictGuiceModule(),
                new RegistrationEnvironmentModule(),
                new GitHubClientsModule(),
                new HttpClientModule()
        );
        RegisterExecutor registerExecutor = injector.getInstance(RegisterExecutor.class);
        registerExecutor.execute(args[1]);
    }

    private static void registerSource(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Register Source requires a JSON input");
        }
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                new StrictGuiceModule(),
                new SourceRegistrationEnvironmentModule(),
                new GitHubClientsModule()
        );
        RegisterSourceExecutor registerSourceExecutor = injector.getInstance(RegisterSourceExecutor.class);
        registerSourceExecutor.execute(args[1]);
    }

    private static void materializeAllSoftware(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Materialize All Software takes no inputs");
        }
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                new StrictGuiceModule(),
                new MaterializationEnvironmentModule(),
                new GitHubClientsModule(),
                new HttpClientModule()
        );
        MaterializeAllSoftwareExecutor materializeAllSoftwareExecutor = injector.getInstance(
            MaterializeAllSoftwareExecutor.class
        );
        materializeAllSoftwareExecutor.execute();
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
