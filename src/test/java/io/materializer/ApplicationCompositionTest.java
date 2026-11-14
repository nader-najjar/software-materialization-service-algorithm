package io.materializer;
import java.net.http.HttpClient;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Stage;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import io.materializer.composition.GitHubClientsModule;
import io.materializer.composition.HttpClientModule;
import io.materializer.composition.MaterializationEnvironmentModule;
import io.materializer.composition.RegistrationEnvironmentModule;
import io.materializer.composition.StrictGuiceModule;
import io.materializer.execution.MaterializeSoftwareExecutor;
import io.materializer.execution.RegisterExecutor;
import io.materializer.shared.githubcredentialstore.GitHubCredentialStore;
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
                        new RegistrationEnvironmentModule(),
                        new GitHubClientsModule(),
                        new HttpClientModule()
                ).with(binder -> {
                    binder.bind(String.class).annotatedWith(Names.named("materializationServiceRepository"))
                            .toInstance("owner/service");
                    binder.bind(String.class).annotatedWith(Names.named("materializationDispatchClientID"))
                            .toInstance("client-id");
                    binder.bind(String.class).annotatedWith(Names.named("materializationDispatchPrivateKey"))
                            .toInstance("private-key");
                    binder.bind(String.class).annotatedWith(Names.named("materializationPublishClientID"))
                            .toInstance("client-id");
                    binder.bind(String.class).annotatedWith(Names.named("materializationPublishPrivateKey"))
                            .toInstance("private-key");
                    binder.bind(String.class).annotatedWith(Names.named("githubToken")).toInstance("token");
                    binder.bind(String.class).annotatedWith(Names.named("materializerRegistryRepository"))
                            .toInstance("owner/registry");
                    binder.bind(String.class).annotatedWith(Names.named("pipelineInputsRepository"))
                            .toInstance("owner/inputs");
                    binder.bind(GitHub.class).toInstance(mock(GitHub.class));
                    binder.bind(HttpClient.class).toInstance(mock(HttpClient.class));
                })
        );

        assertNotNull(injector.getInstance(RegisterExecutor.class));
        assertNull(injector.getExistingBinding(Key.get(MaterializeSoftwareExecutor.class)));
        assertNotNull(injector.getInstance(HttpClient.class));
        assertNotNull(injector.getInstance(Key.get(String.class, Names.named("githubToken"))));
        assertNotNull(injector.getInstance(GitHubCredentialStore.class));
    }

    @Test
    void materializationDoesNotConstructRegistrationDependencies() {
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                Modules.override(
                        new StrictGuiceModule(),
                        new MaterializationEnvironmentModule(),
                        new GitHubClientsModule(),
                        new HttpClientModule()
                ).with(binder -> {
                    binder.bind(String.class).annotatedWith(Names.named("githubToken")).toInstance("token");
                    binder.bind(String.class).annotatedWith(Names.named("materializerRegistryRepository"))
                            .toInstance("owner/registry");
                    binder.bind(String.class).annotatedWith(Names.named("pipelineInputsRepository"))
                            .toInstance("owner/inputs");
                    binder.bind(String.class).annotatedWith(Names.named("materializationServiceRepository"))
                            .toInstance("owner/service");
                    binder.bind(String.class).annotatedWith(Names.named("materializationDispatchClientID"))
                            .toInstance("client-id");
                    binder.bind(String.class).annotatedWith(Names.named("materializationDispatchPrivateKey"))
                            .toInstance("private-key");
                    binder.bind(HttpClient.class).toInstance(mock(HttpClient.class));
                    binder.bind(GitHub.class).toInstance(mock(GitHub.class));
                })
        );

        assertNotNull(injector.getInstance(MaterializeSoftwareExecutor.class));
        assertNull(injector.getExistingBinding(Key.get(RegisterExecutor.class)));
        Key<String> dispatcherKey = Key.get(String.class, Names.named("materializationDispatchPrivateKey"));
        assertNotNull(injector.getExistingBinding(dispatcherKey));
        assertNotNull(injector.getExistingBinding(Key.get(GitHubCredentialStore.class)));
    }
}
