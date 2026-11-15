package io.materializer.execution;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import io.materializer.notifyserviceworkflowgeneration.NotifyServiceWorkflowGenerator;
import io.materializer.notifyserviceworkflowpublishing.NotifyServiceWorkflowPublisher;
import io.materializer.shared.githubcredentialstore.GitHubCredentialStore;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RegisterExecutorTest {

    private final MaterializerRegistryStore materializerRegistryStore = mock(MaterializerRegistryStore.class);
    private final LivePipelineInputsStore livePipelineInputsStore = mock(LivePipelineInputsStore.class);
    private final GitHubCredentialStore gitHubCredentialStore = mock(GitHubCredentialStore.class);
    private final NotifyServiceWorkflowGenerator notifyServiceWorkflowGenerator =
            mock(NotifyServiceWorkflowGenerator.class);
    private final NotifyServiceWorkflowPublisher notifyServiceWorkflowPublisher =
            mock(NotifyServiceWorkflowPublisher.class);
    private final GitHub gitHub = mock(GitHub.class);
    private final RegisterExecutor registerExecutor = new RegisterExecutor(
            gitHub,
            materializerRegistryStore,
            livePipelineInputsStore,
            gitHubCredentialStore,
            "client-id",
            "private-key",
            notifyServiceWorkflowGenerator,
            notifyServiceWorkflowPublisher
    );
    private static final String INPUT = "{\"repositoryName\":\"owner/materializer\"}";

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "{}", "{\"repositoryName\":42}", "{\"repositoryName\":\"invalid\"}"})
    void rejectsInvalidInputBeforeCheckingRegistration(String json) {
        assertThrows(IllegalArgumentException.class, () -> registerExecutor.execute(json));

        verifyNoInteractions(materializerRegistryStore, livePipelineInputsStore, gitHubCredentialStore);
    }

    @Test
    void checksFreshnessBeforeWritingDispatcherCredentials() throws IOException {
        prepareRegistry(List.of());

        registerExecutor.execute("""
                {"repositoryName":"owner/materializer","sourceRepositories":["owner/source"]}
                """);

        InOrder order = inOrder(
                materializerRegistryStore,
                livePipelineInputsStore,
                gitHubCredentialStore,
                notifyServiceWorkflowPublisher
        );
        order.verify(materializerRegistryStore).readSnapshot();
        order.verify(livePipelineInputsStore).read("owner/materializer");
        order.verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID");
        order.verify(gitHubCredentialStore).secretExists("owner/materializer", "MATERIALIZATION_DISPATCH_PRIVATE_KEY");
        order.verify(gitHubCredentialStore).setVariable(
                "owner/materializer",
                "MATERIALIZATION_DISPATCH_CLIENT_ID",
                "client-id"
        );
        order.verify(gitHubCredentialStore).setSecret(
                "owner/materializer",
                "MATERIALIZATION_DISPATCH_PRIVATE_KEY",
                "private-key"
        );
        order.verify(notifyServiceWorkflowPublisher).publish(
                "owner/materializer",
                "development",
                ".github/workflows/notify-service.yml",
                "workflow"
        );
        order.verify(materializerRegistryStore).addRegistration(
                new MaterializerRegistrySnapshot(new MaterializerRegistry(List.of()), "file-sha"),
                new MaterializerRegistration("owner/materializer", List.of("owner/source"))
        );
        verifyNoMoreInteractions(materializerRegistryStore, livePipelineInputsStore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner/materializer", "OWNER/Materializer"})
    void rejectsExistingRegistration(String repositoryName) throws IOException {
        prepareRegistry(List.of(new MaterializerRegistration(repositoryName, List.of())));

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));

        verifyNoInteractions(livePipelineInputsStore, gitHubCredentialStore);
        verify(materializerRegistryStore).readSnapshot();
        verifyNoMoreInteractions(materializerRegistryStore);
    }

    @Test
    void rejectsExistingInputs() throws IOException {
        prepareRegistry(List.of());
        when(livePipelineInputsStore.read("owner/materializer"))
                .thenReturn(Optional.of(mock(LivePipelineInputs.class)));

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));
        verifyNoInteractions(gitHubCredentialStore);
        verify(materializerRegistryStore).readSnapshot();
        verify(livePipelineInputsStore).read("owner/materializer");
        verifyNoMoreInteractions(materializerRegistryStore, livePipelineInputsStore);
    }

    @Test
    void propagatesAnInconclusiveSnapshotLookup() throws IOException {
        prepareRegistry(List.of());
        when(livePipelineInputsStore.read("owner/materializer"))
                .thenThrow(new UncheckedIOException(new IOException("Denied")));

        assertThrows(UncheckedIOException.class, () -> registerExecutor.execute(INPUT));
    }

    @Test
    void rejectsExistingDispatcherClientIDBeforeCheckingSecret() throws IOException {
        prepareRegistry(List.of());
        when(gitHubCredentialStore.variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID"))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));
        verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID");
        verifyNoMoreInteractions(gitHubCredentialStore);
    }

    @Test
    void rejectsExistingDispatcherPrivateKey() throws IOException {
        prepareRegistry(List.of());
        when(gitHubCredentialStore.secretExists("owner/materializer", "MATERIALIZATION_DISPATCH_PRIVATE_KEY"))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));
        verify(gitHubCredentialStore).secretExists("owner/materializer", "MATERIALIZATION_DISPATCH_PRIVATE_KEY");
        verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID");
        verifyNoMoreInteractions(gitHubCredentialStore);
    }

    @Test
    void stopsProvisioningIfVariableWriteFails() throws IOException {
        prepareRegistry(List.of());
        doThrow(new IllegalStateException("Write failed")).when(gitHubCredentialStore).setVariable(
                "owner/materializer",
                "MATERIALIZATION_DISPATCH_CLIENT_ID",
                "client-id"
        );

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));

        verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID");
        verify(gitHubCredentialStore).secretExists("owner/materializer", "MATERIALIZATION_DISPATCH_PRIVATE_KEY");
        verify(gitHubCredentialStore).setVariable(
                "owner/materializer",
                "MATERIALIZATION_DISPATCH_CLIENT_ID",
                "client-id"
        );
        verifyNoMoreInteractions(gitHubCredentialStore);
    }

    @Test
    void doesNotSaveRegistrationWhenWorkflowPublicationFails() throws IOException {
        prepareRegistry(List.of());
        doThrow(new IllegalStateException("Publication failed")).when(notifyServiceWorkflowPublisher)
                .publish(
                "owner/materializer",
                "development",
                ".github/workflows/notify-service.yml",
                "workflow"
        );

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));

        verify(materializerRegistryStore).readSnapshot();
        verifyNoMoreInteractions(materializerRegistryStore);
    }

    private void prepareRegistry(List<MaterializerRegistration> registrations) throws IOException {
        GHRepository repository = mock(GHRepository.class);
        when(gitHub.getRepository("owner/materializer")).thenReturn(repository);
        when(repository.getDefaultBranch()).thenReturn("development");
        when(notifyServiceWorkflowGenerator.generate("development"))
                .thenReturn("workflow");
        when(materializerRegistryStore.readSnapshot()).thenReturn(
                new MaterializerRegistrySnapshot(new MaterializerRegistry(registrations), "file-sha")
        );
    }
}
