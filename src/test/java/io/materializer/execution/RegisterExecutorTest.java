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
    private final RegisterExecutor registerExecutor = new RegisterExecutor(
            materializerRegistryStore,
            livePipelineInputsStore,
            new RegistrationCredentials(
                    gitHubCredentialStore,
                    "client-id",
                    "private-key",
                    "publish-client-id",
                    "publish-private-key"
            ),
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
    void checksFreshnessBeforeWritingDispatcherCredentials() {
        prepareRegistry(List.of());

        registerExecutor.execute(INPUT);

        var order = inOrder(
                materializerRegistryStore,
                livePipelineInputsStore,
                gitHubCredentialStore,
                notifyServiceWorkflowPublisher
        );
        order.verify(materializerRegistryStore).readSnapshot();
        order.verify(livePipelineInputsStore).read("owner/materializer");
        order.verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID");
        order.verify(gitHubCredentialStore).secretExists("owner/materializer", "MATERIALIZATION_DISPATCH_PRIVATE_KEY");
        order.verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_PUBLISH_CLIENT_ID");
        order.verify(gitHubCredentialStore).secretExists("owner/materializer", "MATERIALIZATION_PUBLISH_PRIVATE_KEY");
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
        order.verify(gitHubCredentialStore).setVariable(
                "owner/materializer",
                "MATERIALIZATION_PUBLISH_CLIENT_ID",
                "publish-client-id"
        );
        order.verify(gitHubCredentialStore).setSecret(
                "owner/materializer",
                "MATERIALIZATION_PUBLISH_PRIVATE_KEY",
                "publish-private-key"
        );
        order.verify(notifyServiceWorkflowPublisher).publish(
                "owner/materializer",
                "main",
                ".github/workflows/notify-service.yml",
                "workflow"
        );
        order.verify(materializerRegistryStore).addRegistration(
                new MaterializerRegistrySnapshot(new MaterializerRegistry(List.of()), "file-sha"),
                "owner/materializer"
        );
        verifyNoMoreInteractions(materializerRegistryStore, livePipelineInputsStore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner/materializer", "OWNER/Materializer"})
    void rejectsExistingRegistration(String repositoryName) {
        prepareRegistry(List.of(new MaterializerRegistration(repositoryName)));

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));

        verifyNoInteractions(livePipelineInputsStore, gitHubCredentialStore);
        verify(materializerRegistryStore).readSnapshot();
        verifyNoMoreInteractions(materializerRegistryStore);
    }

    @Test
    void rejectsExistingInputs() {
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
    void propagatesAnInconclusiveSnapshotLookup() {
        prepareRegistry(List.of());
        when(livePipelineInputsStore.read("owner/materializer"))
                .thenThrow(new UncheckedIOException(new IOException("Denied")));

        assertThrows(UncheckedIOException.class, () -> registerExecutor.execute(INPUT));
    }

    @Test
    void rejectsExistingDispatcherClientIDBeforeCheckingSecret() {
        prepareRegistry(List.of());
        when(gitHubCredentialStore.variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID"))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));
        verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID");
        verifyNoMoreInteractions(gitHubCredentialStore);
    }

    @Test
    void rejectsExistingDispatcherPrivateKey() {
        prepareRegistry(List.of());
        when(gitHubCredentialStore.secretExists("owner/materializer", "MATERIALIZATION_DISPATCH_PRIVATE_KEY"))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));
        verify(gitHubCredentialStore).secretExists("owner/materializer", "MATERIALIZATION_DISPATCH_PRIVATE_KEY");
        verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID");
        verifyNoMoreInteractions(gitHubCredentialStore);
    }

    @Test
    void stopsProvisioningIfVariableWriteFails() {
        prepareRegistry(List.of());
        doThrow(new IllegalStateException("Write failed")).when(gitHubCredentialStore).setVariable(
                "owner/materializer",
                "MATERIALIZATION_DISPATCH_CLIENT_ID",
                "client-id"
        );

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));

        verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_DISPATCH_CLIENT_ID");
        verify(gitHubCredentialStore).secretExists("owner/materializer", "MATERIALIZATION_DISPATCH_PRIVATE_KEY");
        verify(gitHubCredentialStore).variableExists("owner/materializer", "MATERIALIZATION_PUBLISH_CLIENT_ID");
        verify(gitHubCredentialStore).secretExists("owner/materializer", "MATERIALIZATION_PUBLISH_PRIVATE_KEY");
        verify(gitHubCredentialStore).setVariable(
                "owner/materializer",
                "MATERIALIZATION_DISPATCH_CLIENT_ID",
                "client-id"
        );
        verifyNoMoreInteractions(gitHubCredentialStore);
    }

    @Test
    void doesNotSaveRegistrationWhenWorkflowPublicationFails() {
        prepareRegistry(List.of());
        doThrow(new IllegalStateException("Publication failed")).when(notifyServiceWorkflowPublisher)
                .publish(
                "owner/materializer",
                "main",
                ".github/workflows/notify-service.yml",
                "workflow"
        );

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));

        verify(materializerRegistryStore).readSnapshot();
        verifyNoMoreInteractions(materializerRegistryStore);
    }

    private void prepareRegistry(List<MaterializerRegistration> registrations) {
        when(notifyServiceWorkflowPublisher.defaultBranch("owner/materializer")).thenReturn("main");
        when(notifyServiceWorkflowGenerator.generate("owner/materializer", "main"))
                .thenReturn("workflow");
        when(materializerRegistryStore.readSnapshot()).thenReturn(
                new MaterializerRegistrySnapshot(new MaterializerRegistry(registrations), "file-sha")
        );
    }
}
