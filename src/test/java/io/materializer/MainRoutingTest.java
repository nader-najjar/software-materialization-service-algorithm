package io.materializer;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import io.materializer.composition.GitHubClientsModule;
import io.materializer.composition.GitHubTokenModule;
import io.materializer.composition.HttpClientModule;
import io.materializer.composition.MaterializerRegistryRepositoryModule;
import io.materializer.composition.PipelineInputsRepositoryModule;
import io.materializer.composition.StrictGuiceModule;
import io.materializer.execution.MaterializeNewPushesExecutor;
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
                    any(GitHubTokenModule.class),
                    any(MaterializerRegistryRepositoryModule.class),
                    any(GitHubClientsModule.class)
            ))
                    .thenReturn(injector);

            Main.route(new String[] {"register", "{\"repositoryName\":\"owner/pipeline\"}"});

            verify(registerExecutor).execute("{\"repositoryName\":\"owner/pipeline\"}");
            guice.verify(() -> Guice.createInjector(
                    eq(Stage.PRODUCTION),
                    any(StrictGuiceModule.class),
                    any(GitHubTokenModule.class),
                    any(MaterializerRegistryRepositoryModule.class),
                    any(GitHubClientsModule.class)
            ));
            guice.verifyNoMoreInteractions();
        }
    }

    @Test
    void constructsOnlyMaterializationComposition() {
        Injector injector = mock(Injector.class);
        MaterializeNewPushesExecutor materializeNewPushesExecutor = mock(MaterializeNewPushesExecutor.class);
        when(injector.getInstance(MaterializeNewPushesExecutor.class)).thenReturn(materializeNewPushesExecutor);
        try (var guice = mockStatic(Guice.class)) {
            guice.when(() -> Guice.createInjector(
                    eq(Stage.PRODUCTION),
                    any(StrictGuiceModule.class),
                    any(GitHubTokenModule.class),
                    any(MaterializerRegistryRepositoryModule.class),
                    any(PipelineInputsRepositoryModule.class),
                    any(GitHubClientsModule.class),
                    any(HttpClientModule.class)
            ))
                    .thenReturn(injector);

            Main.route(new String[] {"materialize-new-pushes", "{}"});

            verify(materializeNewPushesExecutor).execute("{}");
            guice.verify(() -> Guice.createInjector(
                    eq(Stage.PRODUCTION),
                    any(StrictGuiceModule.class),
                    any(GitHubTokenModule.class),
                    any(MaterializerRegistryRepositoryModule.class),
                    any(PipelineInputsRepositoryModule.class),
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
