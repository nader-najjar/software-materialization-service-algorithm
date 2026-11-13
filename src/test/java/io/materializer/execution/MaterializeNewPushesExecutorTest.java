package io.materializer.execution;
import java.util.List;
import java.util.Optional;

import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.workflowgeneration.WorkflowGenerator;
import io.materializer.workflowpublishing.WorkflowPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaterializeNewPushesExecutorTest {

    private final MaterializerRegistryStore materializerRegistryStore = mock(MaterializerRegistryStore.class);
    private final LivePipelineInputsAdvancer livePipelineInputsAdvancer = mock(LivePipelineInputsAdvancer.class);
    private final LivePipelineInputsReconciler livePipelineInputsReconciler = mock(LivePipelineInputsReconciler.class);
    private final WorkflowGenerator workflowGenerator = mock(WorkflowGenerator.class);
    private final WorkflowPublisher workflowPublisher = mock(WorkflowPublisher.class);
    private final MaterializeNewPushesExecutor materializeNewPushesExecutor = new MaterializeNewPushesExecutor(
            livePipelineInputsAdvancer,
            livePipelineInputsReconciler,
            workflowGenerator,
            workflowPublisher,
            materializerRegistryStore
    );
    private final MaterializerRegistration registration = new MaterializerRegistration("owner/materializer");

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "{"})
    void rejectsInvalidInputBeforeReadingTheRegistry(String json) {
        assertThrows(IllegalArgumentException.class, () -> materializeNewPushesExecutor.execute(json));

        verifyNoInteractions(materializerRegistryStore, livePipelineInputsAdvancer);
    }

    @Test
    void doesNothingForAnEmptyRegistry() {
        when(materializerRegistryStore.read()).thenReturn(new MaterializerRegistry(List.of()));

        execute();

        verifyNoInteractions(
                livePipelineInputsAdvancer,
                livePipelineInputsReconciler,
                workflowGenerator,
                workflowPublisher
        );
    }

    @Test
    void doesNothingWhenRegisteredPipelineHasNoUpdate() {
        when(materializerRegistryStore.read()).thenReturn(new MaterializerRegistry(List.of(registration)));
        when(livePipelineInputsAdvancer.advance(registration.repositoryName())).thenReturn(Optional.empty());

        execute();

        verifyNoInteractions(livePipelineInputsReconciler, workflowGenerator, workflowPublisher);
    }

    @Test
    void propagatesRegistryFailures() {
        when(materializerRegistryStore.read()).thenThrow(new IllegalArgumentException("Invalid registry"));

        assertThrows(IllegalArgumentException.class, this::execute);

        verifyNoInteractions(
                livePipelineInputsAdvancer,
                livePipelineInputsReconciler,
                workflowGenerator,
                workflowPublisher
        );
    }

    @Test
    void generatesAndPublishesFromReconciledInputs() {
        when(materializerRegistryStore.read()).thenReturn(new MaterializerRegistry(List.of(registration)));
        LivePipelineInputs advanced = inputs(registration.repositoryName());
        LivePipelineInputs reconciled = prepareSuccessfulRegistration(registration);

        execute();

        InOrder order = inOrder(
                livePipelineInputsAdvancer,
                livePipelineInputsReconciler,
                workflowGenerator,
                workflowPublisher
        );
        order.verify(livePipelineInputsAdvancer).advance(registration.repositoryName());
        order.verify(livePipelineInputsReconciler).reconcile(advanced);
        order.verify(workflowGenerator).generate(reconciled);
        order.verify(workflowPublisher).publish(registration.repositoryName(), "workflow");
    }

    @ParameterizedTest
    @ValueSource(strings = {"advance", "reconcile", "generate", "publish"})
    void continuesToNextRegistrationAfterFailure(String stage) {
        MaterializerRegistration broken = new MaterializerRegistration("owner/broken");
        when(materializerRegistryStore.read()).thenReturn(new MaterializerRegistry(List.of(broken, registration)));
        prepareFailure(broken, stage);
        LivePipelineInputs reconciled = prepareSuccessfulRegistration(registration);

        execute();

        InOrder order = inOrder(workflowGenerator, workflowPublisher);
        order.verify(workflowGenerator).generate(reconciled);
        order.verify(workflowPublisher).publish(registration.repositoryName(), "workflow");
    }

    private void execute() {
        materializeNewPushesExecutor.execute("{}");
    }

    private LivePipelineInputs prepareSuccessfulRegistration(MaterializerRegistration materializerRegistration) {
        LivePipelineInputs advanced = inputs(materializerRegistration.repositoryName());
        RepositorySnapshot source = new RepositorySnapshot("owner/source", "main", "b".repeat(40), 2L);
        RepositorySnapshot materializerSnapshot = advanced.materializerRepositorySnapshot();
        LivePipelineInputs reconciled = new LivePipelineInputs(materializerSnapshot, List.of(source));
        when(livePipelineInputsAdvancer.advance(materializerRegistration.repositoryName()))
                .thenReturn(Optional.of(advanced));
        when(livePipelineInputsReconciler.reconcile(advanced)).thenReturn(reconciled);
        when(workflowGenerator.generate(reconciled)).thenReturn("workflow");
        return reconciled;
    }

    private void prepareFailure(MaterializerRegistration materializerRegistration, String stage) {
        LivePipelineInputs advanced = inputs(materializerRegistration.repositoryName());
        LivePipelineInputs reconciled = prepareSuccessfulRegistration(materializerRegistration);
        IllegalStateException failure = new IllegalStateException("Failed " + stage);
        switch (stage) {
            case "advance" -> when(livePipelineInputsAdvancer.advance(materializerRegistration.repositoryName()))
                    .thenThrow(failure);
            case "reconcile" -> when(livePipelineInputsReconciler.reconcile(advanced)).thenThrow(failure);
            case "generate" -> when(workflowGenerator.generate(reconciled)).thenThrow(failure);
            case "publish" -> {
                doThrow(failure).when(workflowPublisher).publish(materializerRegistration.repositoryName(), "workflow");
            }
            default -> throw new IllegalArgumentException(stage);
        }
    }

    private LivePipelineInputs inputs(String repositoryName) {
        RepositorySnapshot snapshot = new RepositorySnapshot(repositoryName, "main", "a".repeat(40), 1L);
        return new LivePipelineInputs(snapshot, List.of());
    }
}
