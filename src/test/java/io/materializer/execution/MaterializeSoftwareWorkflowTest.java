package io.materializer.execution;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.pipelineworkflowgeneration.BuildMatrixGenerator;
import io.materializer.pipelineworkflowgeneration.PipelineWorkflowGenerator;
import io.materializer.pipelineworkflowpublishing.PipelineWorkflowPublisher;
import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.models.PushEvent;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.ResourceUtility;
import io.materializer.shared.utilities.YamlMapperUtility;
import io.materializer.sourcenotificationreconciler.SourceNotificationReconciler;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.GitHub;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterializeSoftwareWorkflowTest {

    private final LivePipelineInputsAdvancer livePipelineInputsAdvancer = mock(LivePipelineInputsAdvancer.class);
    private final LivePipelineInputsReconciler livePipelineInputsReconciler = mock(LivePipelineInputsReconciler.class);
    private final MaterializerRegistryStore materializerRegistryStore = mock(MaterializerRegistryStore.class);
    private final MaterializerRegistration registration = new MaterializerRegistration("owner/materializer");
    private final LivePipelineDeclarationFetcher livePipelineDeclarationFetcher =
            mock(LivePipelineDeclarationFetcher.class);
    private final SourceNotificationReconciler sourceNotificationReconciler = mock(SourceNotificationReconciler.class);
    private final LivePipelineInputsStore livePipelineInputsStore = mock(LivePipelineInputsStore.class);
    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GHTreeBuilder treeBuilder = mock(GHTreeBuilder.class, RETURNS_SELF);
    private final GHCommitBuilder commitBuilder = mock(GHCommitBuilder.class, RETURNS_SELF);
    private final LivePipelineInputs reconciledInputs = new LivePipelineInputs(
            new RepositorySnapshot("owner/materializer", "main", "a".repeat(40)),
            List.of(new RepositorySnapshot("owner/source", "main", "b".repeat(40)))
    );
    private final MaterializeSoftwareExecutor materializeSoftwareExecutor = new MaterializeSoftwareExecutor(
            livePipelineInputsAdvancer,
            livePipelineInputsReconciler,
            sourceNotificationReconciler,
            livePipelineInputsStore,
            new PipelineWorkflowGenerator(livePipelineDeclarationFetcher, new BuildMatrixGenerator()),
            new PipelineWorkflowPublisher(gitHub),
            materializerRegistryStore
    );

    @Test
    void publishesGeneratedWorkflowAndActionsFromReconciledInputs() throws IOException {
        preparePublication();
        materializeSoftwareExecutor.execute(input());

        InOrder order = inOrder(sourceNotificationReconciler, livePipelineInputsStore, treeBuilder, repository);
        order.verify(sourceNotificationReconciler).reconcile(any(), eq(reconciledInputs));
        order.verify(livePipelineInputsStore).write(reconciledInputs);
        ArgumentCaptor<String> yaml = ArgumentCaptor.forClass(String.class);
        order.verify(treeBuilder).add(eq(".github/workflows/materialize.yml"), yaml.capture(), eq(false));
        verifyPublishedActions(order);
        order.verify(treeBuilder).create();
        order.verify(repository).createRef("refs/heads/execution", "published-commit");

        JsonNode workflow = YamlMapperUtility.MAPPER.readTree(yaml.getValue());
        JsonNode job = workflow.path("jobs").path("build");
        JsonNode row = job.path("strategy").path("matrix").path("include").get(0);
        assertEquals("owner/source", row.path("repositoryName").asText());
        assertEquals("b".repeat(40), row.path("commitSHA").asText());
        assertEquals(9, job.path("steps").size());
    }

    @Test
    void handlesExecutionBranchPublicationFailure() throws IOException {
        preparePublication();
        IOException failure = new IOException("GitHub unavailable");
        when(repository.createRef("refs/heads/execution", "published-commit")).thenThrow(failure);

        assertThrows(java.io.UncheckedIOException.class, () -> materializeSoftwareExecutor.execute(input()));
        verify(treeBuilder).create();
    }

    @Test
    void stopsBeforeSavingWhenNotificationsFail() throws IOException {
        preparePublication();
        org.mockito.Mockito.doThrow(new IllegalStateException("Publication failed")).when(sourceNotificationReconciler)
                .reconcile(any(), eq(reconciledInputs));

        assertThrows(IllegalStateException.class, () -> materializeSoftwareExecutor.execute(input()));
        org.mockito.Mockito.verifyNoInteractions(livePipelineInputsStore, treeBuilder);
    }

    private void preparePublication() throws IOException {
        when(materializerRegistryStore.read()).thenReturn(new MaterializerRegistry(List.of(registration)));
        LivePipelineInputs advancedInputs = new LivePipelineInputs(
                reconciledInputs.materializerRepositorySnapshot(),
                List.of()
        );
        when(livePipelineInputsAdvancer.advance(eq(registration.repositoryName()), any(PushEvent.class)))
                .thenReturn(advancedInputs);
        when(livePipelineInputsReconciler.reconcile(advancedInputs)).thenReturn(reconciledInputs);
        DeclaredPipelineInput input = new DeclaredPipelineInput(
                "owner/source",
                "main",
                "universal",
                List.of("x86_64-linux")
        );
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(
                List.of(input),
                List.of(new DeclaredPipelineStep("Build", "build"))
        );
        when(livePipelineDeclarationFetcher.fetchAndValidate(reconciledInputs.materializerRepositorySnapshot()))
                .thenReturn(declaration);
        when(gitHub.getRepository("owner/materializer")).thenReturn(repository);
        when(repository.getBranches()).thenReturn(Map.of());
        when(repository.createTree()).thenReturn(treeBuilder);
        when(repository.createCommit()).thenReturn(commitBuilder);
        GHTree tree = mock(GHTree.class);
        when(treeBuilder.create()).thenReturn(tree);
        when(tree.getSha()).thenReturn("workflow-tree");
        GHCommit commit = mock(GHCommit.class);
        when(commitBuilder.create()).thenReturn(commit);
        when(commit.getSHA1()).thenReturn("published-commit");
    }

    private void verifyPublishedActions(InOrder order) throws IOException {
        for (String resource : List.of(
                "actions/reject-manual-retries/action.yml",
                "actions/reject-manual-retries/check.sh",
                "actions/universal-build/action.yml"
        )) {
            order.verify(treeBuilder).add(".github/" + resource, ResourceUtility.readString("/" + resource), false);
        }
    }

    private String input() {
        return """
                {"pipelineRepository":"owner/materializer","pushEvent":{
                  "repository":{"full_name":"owner/materializer"},"ref":"refs/heads/main",
                  "before":"%s","after":"%s","deleted":false}}
                """.replace("\n", "").formatted("a".repeat(40), "b".repeat(40));
    }

}
