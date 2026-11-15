package io.materializer.livepipelineinputsreconciler;
import java.io.UncheckedIOException;
import java.util.List;

import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.repositorypushhistory.RepositoryPushHistory;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LivePipelineInputsReconcilerTest {

    private final LivePipelineDeclarationFetcher livePipelineDeclarationFetcher =
            mock(LivePipelineDeclarationFetcher.class);
    private final RepositoryPushHistory repositoryPushHistory = mock(RepositoryPushHistory.class);
    private final LivePipelineInputsReconciler livePipelineInputsReconciler = new LivePipelineInputsReconciler(
            livePipelineDeclarationFetcher,
            repositoryPushHistory
    );
    private final RepositorySnapshot materializerSnapshot = new RepositorySnapshot(
            "owner/materializer",
            "main",
            "a".repeat(40),
            1L
    );

    private final MaterializerRegistration registration = new MaterializerRegistration(
            "owner/materializer", List.of("owner/new", "owner/first", "owner/second"));

    private final LivePipelineInputs advanced = new LivePipelineInputs(materializerSnapshot, List.of());

    @Test
    void fetchesAndValidatesThePinnedMaterializerDeclaration() {
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                List.of(declaredInput("owner/new", "main")),
                List.of(new DeclaredPipelineStep("Build", "build", "APP_ID", "APP_KEY"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenReturn(declaration);

        RepositorySnapshot newSnapshot = new RepositorySnapshot("owner/new", "main", "b".repeat(40), 1L);
        prepareLatestSnapshot(newSnapshot);

        LivePipelineInputs result = livePipelineInputsReconciler.reconcile(registration, advanced);


        assertEquals(new LivePipelineInputs(materializerSnapshot, List.of(newSnapshot)), result);
        verify(livePipelineDeclarationFetcher).fetchAndValidate(materializerSnapshot);
        verifyNoMoreInteractions(livePipelineDeclarationFetcher);
    }

    @Test
    void propagatesInvalidDeclarations() {
        IllegalArgumentException failure = new IllegalArgumentException("Invalid pipeline declaration");
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenThrow(failure);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> livePipelineInputsReconciler.reconcile(registration, advanced)
        );

        assertSame(failure, exception);
    }

    @Test
    void fetchesAllDeclaredBranchHeads() {
        prepareChangedDeclaration();
        RepositorySnapshot second = new RepositorySnapshot("OWNER/SECOND", "release", "c".repeat(40), 1L);
        RepositorySnapshot first = new RepositorySnapshot("owner/first", "main", "d".repeat(40), 1L);
        RepositorySnapshot newBranch = new RepositorySnapshot("owner/first", "Main", "e".repeat(40), 1L);
        RepositorySnapshot newRepository = new RepositorySnapshot("owner/new", "main", "f".repeat(40), 1L);
        for (RepositorySnapshot snapshot : List.of(second, first, newBranch, newRepository)) {
            prepareLatestSnapshot(snapshot);
        }
        LivePipelineInputs result = livePipelineInputsReconciler.reconcile(registration, advanced);
        assertEquals(List.of(second, first, newBranch, newRepository), result.declaredRepositorySnapshots());
    }

    @Test
    void rejectsInvalidReconciledInputs() {
        RepositorySnapshot invalidSnapshot = new RepositorySnapshot("owner/new", "main", "invalid", 1L);
        prepareNewRepository(invalidSnapshot);

        assertThrows(ValidationException.class, () -> livePipelineInputsReconciler.reconcile(registration, advanced));

    }

    @Test
    void propagatesLatestSnapshotLookupFailure() {
        prepareNewRepository(new RepositorySnapshot("owner/new", "main", "b".repeat(40), 1L));
        when(repositoryPushHistory.latest("owner/new", "main")).thenThrow(UncheckedIOException.class);

        assertThrows(UncheckedIOException.class, () -> livePipelineInputsReconciler.reconcile(registration, advanced));
    }

    @Test
    void rejectsAnUnapprovedSourceBeforeLookingUpAnyDeclaredBranch() {
        prepareChangedDeclaration();
        MaterializerRegistration partial = new MaterializerRegistration("owner/materializer", List.of("owner/second"));
        assertThrows(IllegalStateException.class, () -> livePipelineInputsReconciler.reconcile(partial, advanced));
        org.mockito.Mockito.verifyNoInteractions(repositoryPushHistory);
    }

    private void prepareLatestSnapshot(RepositorySnapshot snapshot) {
        when(repositoryPushHistory.latest(snapshot.repositoryName(), snapshot.branch())).thenReturn(snapshot);
    }

    private void prepareChangedDeclaration() {
        List<DeclaredPipelineInput> declarations = List.of(
                declaredInput("OWNER/SECOND", "release"),
                declaredInput("owner/first", "main"),
                declaredInput("owner/first", "Main"),
                declaredInput("owner/new", "main")
        );

        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                declarations,
                List.of(new DeclaredPipelineStep("Build", "build", "APP_ID", "APP_KEY"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenReturn(declaration);
    }

    private void prepareNewRepository(RepositorySnapshot snapshot) {
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                List.of(declaredInput("owner/new", "main")),
                List.of(new DeclaredPipelineStep("Build", "build", "APP_ID", "APP_KEY"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenReturn(declaration);
        prepareLatestSnapshot(snapshot);
    }

    private DeclaredPipelineInput declaredInput(String repositoryName, String branch) {
        return new DeclaredPipelineInput(repositoryName, branch, "universal", List.of("x86_64-linux"));
    }

}
