package io.materializer.livepipelineinputsreconciler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LivePipelineInputsReconcilerTest {

    private final LivePipelineDeclarationFetcher livePipelineDeclarationFetcher =
            mock(LivePipelineDeclarationFetcher.class);
    private final GitHub gitHub = mock(GitHub.class);
    private final LivePipelineInputsReconciler livePipelineInputsReconciler = new LivePipelineInputsReconciler(
            livePipelineDeclarationFetcher,
            gitHub
    );
    private final RepositorySnapshot materializerSnapshot = new RepositorySnapshot(
            "owner/materializer",
            "main",
            "a".repeat(40)
    );
    private final LivePipelineInputs inputs = new LivePipelineInputs(materializerSnapshot, List.of());

    @Test
    void fetchesAndValidatesThePinnedMaterializerDeclaration() throws IOException {
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                List.of(declaredInput("owner/new", "main")),
                List.of(new DeclaredPipelineStep("Build", "build"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenReturn(declaration);

        RepositorySnapshot newSnapshot = new RepositorySnapshot("owner/new", "main", "b".repeat(40));
        prepareLatestSnapshot(newSnapshot);

        LivePipelineInputs result = livePipelineInputsReconciler.reconcile(inputs);


        assertEquals(new LivePipelineInputs(materializerSnapshot, List.of(newSnapshot)), result);
        verify(gitHub).getRepository("owner/new");
        verifyNoMoreInteractions(gitHub);
        verify(livePipelineDeclarationFetcher).fetchAndValidate(materializerSnapshot);
        verifyNoMoreInteractions(livePipelineDeclarationFetcher);
    }

    @Test
    void propagatesInvalidDeclarations() {
        IllegalArgumentException failure = new IllegalArgumentException("Invalid pipeline declaration");
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenThrow(failure);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> livePipelineInputsReconciler.reconcile(inputs)
        );

        assertSame(failure, exception);
        verifyNoInteractions(gitHub);
    }

    @Test
    void retainsMatchingPinsAndFetchesOnlyNewRepositoryBranches() throws IOException {
        RepositorySnapshot first = new RepositorySnapshot("owner/first", "main", "b".repeat(40));
        RepositorySnapshot second = new RepositorySnapshot("owner/second", "release", "c".repeat(40));
        RepositorySnapshot removed = new RepositorySnapshot("owner/removed", "main", "d".repeat(40));
        prepareChangedDeclaration();
        List<RepositorySnapshot> previousSnapshots = List.of(first, second, removed);
        LivePipelineInputs previousInputs = new LivePipelineInputs(materializerSnapshot, previousSnapshots);

        RepositorySnapshot newBranch = new RepositorySnapshot("owner/first", "Main", "e".repeat(40));
        RepositorySnapshot newRepository = new RepositorySnapshot("owner/new", "main", "f".repeat(40));
        prepareLatestSnapshot(newBranch);
        prepareLatestSnapshot(newRepository);

        LivePipelineInputs result = livePipelineInputsReconciler.reconcile(previousInputs);
        List<RepositorySnapshot> retained = result.declaredRepositorySnapshots();

        assertSame(materializerSnapshot, result.materializerRepositorySnapshot());
        assertEquals(List.of(second, first, newBranch, newRepository), retained);
        assertSame(second, retained.get(0));
        assertSame(first, retained.get(1));
        verify(gitHub).getRepository("owner/first");
        verify(gitHub).getRepository("owner/new");
        verifyNoMoreInteractions(gitHub);
    }

    @Test
    void rejectsInvalidReconciledInputs() throws IOException {
        RepositorySnapshot invalidSnapshot = new RepositorySnapshot("owner/new", "main", "invalid");
        prepareNewRepository(invalidSnapshot);

        assertThrows(ValidationException.class, () -> livePipelineInputsReconciler.reconcile(inputs));

    }

    @Test
    void propagatesLatestSnapshotLookupFailure() throws IOException {
        prepareNewRepository(new RepositorySnapshot("owner/new", "main", "b".repeat(40)));
        when(gitHub.getRepository("owner/new")).thenThrow(new IOException("Repository unavailable"));

        assertThrows(UncheckedIOException.class, () -> livePipelineInputsReconciler.reconcile(inputs));
    }

    private void prepareLatestSnapshot(RepositorySnapshot snapshot) throws IOException {
        GHRepository repository = mock(GHRepository.class);
        GHBranch branch = mock(GHBranch.class);
        when(gitHub.getRepository(snapshot.repositoryName())).thenReturn(repository);
        when(repository.getBranch(snapshot.branch())).thenReturn(branch);
        when(branch.getSHA1()).thenReturn(snapshot.commitSHA());
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
                List.of(new DeclaredPipelineStep("Build", "build"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenReturn(declaration);
    }

    private void prepareNewRepository(RepositorySnapshot snapshot) throws IOException {
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                List.of(declaredInput("owner/new", "main")),
                List.of(new DeclaredPipelineStep("Build", "build"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(materializerSnapshot)).thenReturn(declaration);
        prepareLatestSnapshot(snapshot);
    }

    private DeclaredPipelineInput declaredInput(String repositoryName, String branch) {
        return new DeclaredPipelineInput(repositoryName, branch, "universal", List.of("x86_64-linux"));
    }

}
