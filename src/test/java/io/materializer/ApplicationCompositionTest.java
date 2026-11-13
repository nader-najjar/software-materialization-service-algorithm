package io.materializer;
import java.net.http.HttpClient;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Stage;
import com.google.inject.util.Modules;
import io.materializer.composition.GitHubClientsModule;
import io.materializer.composition.GitHubTokenModule;
import io.materializer.composition.HttpClientModule;
import io.materializer.composition.MaterializerRegistryRepositoryModule;
import io.materializer.composition.PipelineInputsRepositoryModule;
import io.materializer.composition.StrictGuiceModule;
import io.materializer.execution.MaterializeNewPushesExecutor;
import io.materializer.execution.RegisterExecutor;
import io.materializer.publisherconfiguration.PublisherConfigurator;
import io.materializer.shared.livepipelineinputsstore.models.PipelineInputsRepository;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistryRepository;
import io.materializer.shared.models.GitHubToken;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class ApplicationCompositionTest {

    @Test
    void registrationDoesNotConstructMaterializationDependencies() {
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                Modules.override(
                        new StrictGuiceModule(),
                        new GitHubTokenModule(),
                        new MaterializerRegistryRepositoryModule(),
                        new GitHubClientsModule()
                ).with(binder -> {
                    binder.bind(GitHubToken.class).toInstance(new GitHubToken("token"));
                    binder.bind(MaterializerRegistryRepository.class)
                            .toInstance(new MaterializerRegistryRepository("owner/registry"));
                    binder.bind(GitHub.class).toInstance(mock(GitHub.class));
                })
        );

        assertNotNull(injector.getInstance(RegisterExecutor.class));
        assertNull(injector.getExistingBinding(Key.get(MaterializeNewPushesExecutor.class)));
        assertNull(injector.getExistingBinding(Key.get(HttpClient.class)));
        assertNotNull(injector.getInstance(GitHubToken.class));
        assertNull(injector.getExistingBinding(Key.get(PublisherConfigurator.class)));
    }

    @Test
    void materializationDoesNotConstructRegistrationDependencies() {
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                Modules.override(
                        new StrictGuiceModule(),
                        new GitHubTokenModule(),
                        new MaterializerRegistryRepositoryModule(),
                        new PipelineInputsRepositoryModule(),
                        new GitHubClientsModule(),
                        new HttpClientModule()
                ).with(binder -> {
                    binder.bind(GitHubToken.class).toInstance(new GitHubToken("token"));
                    binder.bind(MaterializerRegistryRepository.class)
                            .toInstance(new MaterializerRegistryRepository("owner/registry"));
                    binder.bind(PipelineInputsRepository.class)
                            .toInstance(new PipelineInputsRepository("owner/inputs"));
                    binder.bind(GitHub.class).toInstance(mock(GitHub.class));
                    binder.bind(HttpClient.class).toInstance(mock(HttpClient.class));
                })
        );

        assertNotNull(injector.getInstance(MaterializeNewPushesExecutor.class));
        assertNull(injector.getExistingBinding(Key.get(RegisterExecutor.class)));
        assertNull(injector.getExistingBinding(Key.get(PublisherConfigurator.class)));
    }
}
