package io.materializer;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import io.materializer.composition.GitHubClientsModule;
import io.materializer.composition.HttpClientModule;
import io.materializer.composition.MaterializationEnvironmentModule;
import io.materializer.composition.RegistrationEnvironmentModule;
import io.materializer.composition.StrictGuiceModule;
import io.materializer.execution.MaterializeSoftwareExecutor;
import io.materializer.execution.RegisterExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainRoutingTest {

    @Test
    void constructsOnlyRegistrationCompositionAndPassesJSON() {
        Injector injector = mock(Injector.class);
        RegisterExecutor registerExecutor = mock(RegisterExecutor.class);
        when(injector.getInstance(RegisterExecutor.class)).thenReturn(registerExecutor);
        try (var guice = mockStatic(Guice.class)) {
            guice.when(() -> Guice.createInjector(
                    eq(Stage.PRODUCTION),
                    any(StrictGuiceModule.class),
                    any(RegistrationEnvironmentModule.class),
                    any(GitHubClientsModule.class),
                    any(HttpClientModule.class)
            ))
                    .thenReturn(injector);

            Main.route(new String[] {"register", "{\"repositoryName\":\"owner/pipeline\"}"});

            verify(registerExecutor).execute("{\"repositoryName\":\"owner/pipeline\"}");
            guice.verify(() -> Guice.createInjector(
                    eq(Stage.PRODUCTION),
                    any(StrictGuiceModule.class),
                    any(RegistrationEnvironmentModule.class),
                    any(GitHubClientsModule.class),
                    any(HttpClientModule.class)
            ));
            guice.verifyNoMoreInteractions();
        }
    }

    @Test
    void constructsOnlyMaterializationComposition() {
        Injector injector = mock(Injector.class);
        MaterializeSoftwareExecutor materializeSoftwareExecutor = mock(MaterializeSoftwareExecutor.class);
        when(injector.getInstance(MaterializeSoftwareExecutor.class)).thenReturn(materializeSoftwareExecutor);
        try (var guice = mockStatic(Guice.class)) {
            guice.when(() -> Guice.createInjector(
                    eq(Stage.PRODUCTION),
                    any(StrictGuiceModule.class),
                    any(MaterializationEnvironmentModule.class),
                    any(GitHubClientsModule.class),
                    any(HttpClientModule.class)
            ))
                    .thenReturn(injector);

            Main.route(new String[] {"materialize-software", "{}"});

            verify(materializeSoftwareExecutor).execute("{}");
            guice.verify(() -> Guice.createInjector(
                    eq(Stage.PRODUCTION),
                    any(StrictGuiceModule.class),
                    any(MaterializationEnvironmentModule.class),
                    any(GitHubClientsModule.class),
                    any(HttpClientModule.class)
            ));
            guice.verifyNoMoreInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "unknown", "REGISTER"})
    void rejectsUnknownOperationsBeforeConstructingAnInjector(String operation) {
        try (var guice = mockStatic(Guice.class)) {
            assertThrows(IllegalArgumentException.class, () -> Main.route(new String[] {operation, "{}"}));
            guice.verifyNoInteractions();
        }
    }

    @Test
    void rejectsMissingOrExtraArgumentsBeforeConstructingAnInjector() {
        try (var guice = mockStatic(Guice.class)) {
            assertThrows(IllegalArgumentException.class, () -> Main.route(null));
            assertThrows(IllegalArgumentException.class, () -> Main.route(new String[0]));
            assertThrows(IllegalArgumentException.class, () -> Main.route(new String[] {"register"}));
            assertThrows(IllegalArgumentException.class, () -> Main.route(new String[] {"register", "{}", "extra"}));
            guice.verifyNoInteractions();
        }
    }
}
