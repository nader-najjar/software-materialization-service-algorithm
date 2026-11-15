package io.materializer.execution;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.pipelineworkflowgeneration.PipelineWorkflowGenerator;
import io.materializer.pipelineworkflowpublishing.PipelineWorkflowPublisher;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.repositorypushhistory.RepositoryPushHistory;
import io.materializer.shared.repositorypushhistory.models.RepositoryPush;
import io.materializer.sourcenotificationreconciler.SourceNotificationReconciler;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaterializeAllSoftwareExecutorTest {

    private final MaterializerRegistryStore materializerRegistryStore = mock(MaterializerRegistryStore.class);
    private final LivePipelineInputsAdvancer advancer = mock(LivePipelineInputsAdvancer.class);
    private final LivePipelineInputsReconciler reconciler = mock(LivePipelineInputsReconciler.class);
    private final LivePipelineInputsStore store = mock(LivePipelineInputsStore.class);
    private final PipelineWorkflowPublisher publisher = mock(PipelineWorkflowPublisher.class);
    private final MaterializeAllSoftwareExecutor materializeAllSoftwareExecutor = new MaterializeAllSoftwareExecutor(
            advancer,
            reconciler,
            mock(SourceNotificationReconciler.class),
            store,
            mock(PipelineWorkflowGenerator.class),
            publisher,
            materializerRegistryStore
    );

    @Test
    void skipsEveryUnchangedRegisteredPipeline() {
        MaterializerRegistration first = new MaterializerRegistration("owner/first", List.of());
        MaterializerRegistration second = new MaterializerRegistration("owner/second", List.of());
        List<MaterializerRegistration> registrations = List.of(first, second);
        when(materializerRegistryStore.read()).thenReturn(new MaterializerRegistry(registrations));
        materializeAllSoftwareExecutor.execute();

        for (MaterializerRegistration registration : registrations) {
            org.mockito.Mockito.verify(advancer).advance(registration.repositoryName());
        }
        verifyNoInteractions(reconciler, publisher);
    }

    @Test
    void advancesPastAnInvalidDeclarationOnTheNextWake() {
        MaterializerRegistration registration = new MaterializerRegistration("owner/pipeline", List.of());
        RepositoryPushHistory history = mock(RepositoryPushHistory.class);
        SourceNotificationReconciler notifications = mock(SourceNotificationReconciler.class);
        MaterializeAllSoftwareExecutor executor = new MaterializeAllSoftwareExecutor(
                new LivePipelineInputsAdvancer(history, store), reconciler,
                notifications, store, mock(PipelineWorkflowGenerator.class),
                publisher, materializerRegistryStore);
        RepositorySnapshot initial = new RepositorySnapshot(registration.repositoryName(), "main", "a".repeat(40), 1L);
        RepositorySnapshot broken = new RepositorySnapshot(registration.repositoryName(), "main", "b".repeat(40), 2L);
        RepositorySnapshot fixed = new RepositorySnapshot(registration.repositoryName(), "main", "c".repeat(40), 3L);
        LivePipelineInputs brokenInputs = new LivePipelineInputs(broken, List.of());
        LivePipelineInputs fixedInputs = new LivePipelineInputs(fixed, List.of());
        AtomicReference<LivePipelineInputs> saved = new AtomicReference<>(new LivePipelineInputs(initial, List.of()));
        when(materializerRegistryStore.read()).thenReturn(new MaterializerRegistry(List.of(registration)));
        when(store.read(registration.repositoryName())).thenAnswer(invocation -> Optional.of(saved.get()));
        org.mockito.Mockito.doAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return null;
        }).when(store).write(org.mockito.ArgumentMatchers.any());
        RepositoryPush badPush = new RepositoryPush(
                2L, initial.commitSHA(), broken.commitSHA(), "2026-11-01T00:00:00Z"
        );
        RepositoryPush fixPush = new RepositoryPush(3L, broken.commitSHA(), fixed.commitSHA(), "2026-11-01T00:01:00Z");
        when(history.since(initial)).thenReturn(List.of(badPush, fixPush));
        when(history.since(broken)).thenReturn(List.of(fixPush));
        when(reconciler.reconcile(registration, brokenInputs)).thenThrow(new IllegalArgumentException("Invalid JSON"));
        when(reconciler.reconcile(registration, fixedInputs)).thenReturn(fixedInputs);

        when(notifications.reconcile(fixedInputs, fixedInputs)).thenReturn(fixedInputs);

        executor.execute();
        org.junit.jupiter.api.Assertions.assertEquals(brokenInputs, saved.get());
        verifyNoInteractions(publisher);
        executor.execute();
        org.junit.jupiter.api.Assertions.assertEquals(fixedInputs, saved.get());
        verifyNoInteractions(publisher);
    }
}
