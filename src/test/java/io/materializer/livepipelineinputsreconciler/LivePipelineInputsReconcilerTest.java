package io.materializer.livepipelineinputsreconciler;
import java.io.UncheckedIOException;
import java.util.List;

import io.materializer.shared.livepipelinedeclarationfetcher.models.BuildStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.repositorypushhistory.RepositoryPushHistory;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LivePipelineInputsReconcilerTest {

    private final RepositoryPushHistory repositoryPushHistory = mock(RepositoryPushHistory.class);
    private final LivePipelineInputsReconciler livePipelineInputsReconciler = new LivePipelineInputsReconciler(
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
    void pinsTheDeclaredRepository() {
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                List.of(declaredInput("owner/new", "main")),
                List.of(new BuildStep("Build", "build", "APP_ID", "APP_KEY"))
        );

        RepositorySnapshot newSnapshot = new RepositorySnapshot("owner/new", "main", "b".repeat(40), 1L);
        prepareLatestSnapshot(newSnapshot);

        LivePipelineInputs result = livePipelineInputsReconciler.reconcile(registration, advanced, declaration);


        assertEquals(new LivePipelineInputs(materializerSnapshot, List.of(newSnapshot)), result);
    }

    @Test
    void fetchesAllDeclaredBranchHeads() {
        LivePipelineDeclaration declaration = changedDeclaration();
        RepositorySnapshot second = new RepositorySnapshot("OWNER/SECOND", "release", "c".repeat(40), 1L);
        RepositorySnapshot first = new RepositorySnapshot("owner/first", "main", "d".repeat(40), 1L);
        RepositorySnapshot newBranch = new RepositorySnapshot("owner/first", "Main", "e".repeat(40), 1L);
        RepositorySnapshot newRepository = new RepositorySnapshot("owner/new", "main", "f".repeat(40), 1L);
        for (RepositorySnapshot snapshot : List.of(second, first, newBranch, newRepository)) {
            prepareLatestSnapshot(snapshot);
        }
        LivePipelineInputs result = livePipelineInputsReconciler.reconcile(registration, advanced, declaration);
        assertEquals(List.of(second, first, newBranch, newRepository), result.declaredRepositorySnapshots());
    }

    @Test
    void rejectsInvalidReconciledInputs() {
        RepositorySnapshot invalidSnapshot = new RepositorySnapshot("owner/new", "main", "invalid", 1L);
        LivePipelineDeclaration declaration = prepareNewRepository(invalidSnapshot);

        assertThrows(
                ValidationException.class,
                () -> livePipelineInputsReconciler.reconcile(registration, advanced, declaration)
        );

    }

    @Test
    void propagatesLatestSnapshotLookupFailure() {
        LivePipelineDeclaration declaration = prepareNewRepository(
                new RepositorySnapshot("owner/new", "main", "b".repeat(40), 1L)
        );
        when(repositoryPushHistory.latest("owner/new", "main")).thenThrow(UncheckedIOException.class);

        assertThrows(
                UncheckedIOException.class,
                () -> livePipelineInputsReconciler.reconcile(registration, advanced, declaration)
        );
    }

    @Test
    void rejectsAnUnapprovedSourceBeforeLookingUpAnyDeclaredBranch() {
        LivePipelineDeclaration declaration = changedDeclaration();
        MaterializerRegistration partial = new MaterializerRegistration("owner/materializer", List.of("owner/second"));
        assertThrows(
                IllegalStateException.class,
                () -> livePipelineInputsReconciler.reconcile(partial, advanced, declaration)
        );
        org.mockito.Mockito.verifyNoInteractions(repositoryPushHistory);
    }

    private void prepareLatestSnapshot(RepositorySnapshot snapshot) {
        when(repositoryPushHistory.latest(snapshot.repositoryName(), snapshot.branch())).thenReturn(snapshot);
    }

    private LivePipelineDeclaration changedDeclaration() {
        List<DeclaredPipelineInput> declarations = List.of(
                declaredInput("OWNER/SECOND", "release"),
                declaredInput("owner/first", "main"),
                declaredInput("owner/first", "Main"),
                declaredInput("owner/new", "main")
        );

        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                declarations,
                List.of(new BuildStep("Build", "build", "APP_ID", "APP_KEY"))
        );
        return declaration;
    }

    private LivePipelineDeclaration prepareNewRepository(RepositorySnapshot snapshot) {
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                List.of(declaredInput("owner/new", "main")),
                List.of(new BuildStep("Build", "build", "APP_ID", "APP_KEY"))
        );
        prepareLatestSnapshot(snapshot);
        return declaration;
    }

    private DeclaredPipelineInput declaredInput(String repositoryName, String branch) {
        return new DeclaredPipelineInput(repositoryName, branch, "universal", List.of("x86_64-linux"));
    }

}
