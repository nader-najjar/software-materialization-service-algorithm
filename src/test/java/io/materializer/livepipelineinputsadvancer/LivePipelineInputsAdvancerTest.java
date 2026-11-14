package io.materializer.livepipelineinputsadvancer;

import java.util.List;
import java.util.Optional;

import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.PushEvent;
import io.materializer.shared.models.RepositorySnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LivePipelineInputsAdvancerTest {

    private final LivePipelineInputsStore livePipelineInputsStore = mock(LivePipelineInputsStore.class);
    private final LivePipelineInputsAdvancer livePipelineInputsAdvancer = new LivePipelineInputsAdvancer(
            livePipelineInputsStore
    );

    @Test
    void initializesFromTheSuppliedPipelinePush() {
        LivePipelineInputs result = livePipelineInputsAdvancer.advance(
                "owner/pipeline",
                push("owner/pipeline", "a", "b")
        );

        assertEquals(
                new RepositorySnapshot("owner/pipeline", "main", "b".repeat(40)),
                result.materializerRepositorySnapshot()
        );
        assertEquals(List.of(), result.declaredRepositorySnapshots());
        verify(livePipelineInputsStore).write(result);
    }

    @Test
    void updatesOnlyThePushedSourceAndAllowsPublicationRetries() {
        RepositorySnapshot pipeline = new RepositorySnapshot("owner/pipeline", "main", "c".repeat(40));
        RepositorySnapshot source = new RepositorySnapshot("owner/source", "main", "a".repeat(40));
        when(livePipelineInputsStore.read("owner/pipeline")).thenReturn(
                Optional.of(new LivePipelineInputs(pipeline, List.of(source)))
        );

        LivePipelineInputs result = livePipelineInputsAdvancer.advance(
                "owner/pipeline",
                push("owner/source", "a", "b")
        );

        assertEquals(pipeline, result.materializerRepositorySnapshot());
        assertEquals("b".repeat(40), result.declaredRepositorySnapshots().getFirst().commitSHA());
        verify(livePipelineInputsStore).write(result);
        when(livePipelineInputsStore.read("owner/pipeline")).thenReturn(Optional.of(result));
        assertEquals(result, livePipelineInputsAdvancer.advance("owner/pipeline", push("owner/source", "a", "b")));
    }

    @Test
    void rejectsUntrackedPushesWithoutSaving() {
        RepositorySnapshot pipeline = new RepositorySnapshot("owner/pipeline", "main", "a".repeat(40));
        when(livePipelineInputsStore.read("owner/pipeline")).thenReturn(
                Optional.of(new LivePipelineInputs(pipeline, List.of()))
        );

        assertThrows(
                IllegalStateException.class,
                () -> livePipelineInputsAdvancer.advance("owner/pipeline", push("owner/unknown", "a", "b"))
        );
        verify(livePipelineInputsStore, never()).write(any());
    }

    @Test
    void acceptsPushWhoseBeforeRevisionDoesNotMatchTheSavedSnapshot() {
        RepositorySnapshot pipeline = new RepositorySnapshot("owner/pipeline", "main", "a".repeat(40));
        when(livePipelineInputsStore.read("owner/pipeline")).thenReturn(
                Optional.of(new LivePipelineInputs(pipeline, List.of()))
        );

        LivePipelineInputs result = livePipelineInputsAdvancer.advance(
                "owner/pipeline",
                push("owner/pipeline", "c", "b")
        );

        assertEquals("b".repeat(40), result.materializerRepositorySnapshot().commitSHA());
        verify(livePipelineInputsStore).write(result);
    }

    @Test
    void rejectsSourceInitializationAndBranchDeletion() {
        assertThrows(
                IllegalStateException.class,
                () -> livePipelineInputsAdvancer.advance("owner/pipeline", push("owner/source", "a", "b"))
        );
        PushEvent deletion = new PushEvent(
                new PushEvent.Repository("owner/pipeline"),
                "refs/heads/main",
                "a".repeat(40),
                "0".repeat(40),
                true
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> livePipelineInputsAdvancer.advance("owner/pipeline", deletion)
        );
        verify(livePipelineInputsStore).read("owner/pipeline");
        verifyNoMoreInteractions(livePipelineInputsStore);
    }

    private PushEvent push(String repository, String before, String after) {
        return new PushEvent(
                new PushEvent.Repository(repository),
                "refs/heads/main",
                before.repeat(40),
                after.repeat(40),
                false
        );
    }
}
