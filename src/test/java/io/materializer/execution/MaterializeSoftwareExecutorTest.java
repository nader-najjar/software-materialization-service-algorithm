package io.materializer.execution;

import java.util.List;

import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.pipelineworkflowgeneration.PipelineWorkflowGenerator;
import io.materializer.pipelineworkflowpublishing.PipelineWorkflowPublisher;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.sourcenotificationreconciler.SourceNotificationReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaterializeSoftwareExecutorTest {

    private final MaterializerRegistryStore materializerRegistryStore = mock(MaterializerRegistryStore.class);
    private final LivePipelineInputsAdvancer livePipelineInputsAdvancer = mock(LivePipelineInputsAdvancer.class);
    private final MaterializeSoftwareExecutor materializeSoftwareExecutor = new MaterializeSoftwareExecutor(
            livePipelineInputsAdvancer,
            mock(LivePipelineInputsReconciler.class),
            mock(SourceNotificationReconciler.class),
            mock(LivePipelineInputsStore.class),
            mock(PipelineWorkflowGenerator.class),
            mock(PipelineWorkflowPublisher.class),
            materializerRegistryStore
    );

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "{", "{}", "{\"pipelineRepository\":\"owner/pipeline\"}"})
    void rejectsInvalidInputBeforeAccessingState(String json) {
        assertThrows(IllegalArgumentException.class, () -> materializeSoftwareExecutor.execute(json));
        verifyNoInteractions(materializerRegistryStore, livePipelineInputsAdvancer);
    }

    @Test
    void rejectsUnregisteredPipelinesBeforeAdvancing() {
        when(materializerRegistryStore.read()).thenReturn(new MaterializerRegistry(List.of()));
        String input = """
                {"pipelineRepository":"owner/pipeline","pushEvent":{
                  "repository":{"full_name":"owner/pipeline"},"ref":"refs/heads/main",
                  "before":"%s","after":"%s","deleted":false}}
                """.replace("\n", "").formatted("a".repeat(40), "b".repeat(40));

        assertThrows(IllegalStateException.class, () -> materializeSoftwareExecutor.execute(input));
        verifyNoInteractions(livePipelineInputsAdvancer);
    }
}
