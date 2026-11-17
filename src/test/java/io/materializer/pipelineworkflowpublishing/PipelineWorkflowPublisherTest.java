package io.materializer.pipelineworkflowpublishing;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitBuilder;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.GitHub;

import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineWorkflowPublisherTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GHTreeBuilder treeBuilder = mock(GHTreeBuilder.class, RETURNS_SELF);
    private final GHCommitBuilder commitBuilder = mock(GHCommitBuilder.class, RETURNS_SELF);
    private final PipelineWorkflowPublisher pipelineWorkflowPublisher = new PipelineWorkflowPublisher(gitHub);

    @Test
    void publishesFirstWorkflowWhenCreatingExecutionBranch() throws IOException {
        preparePublication();
        when(repository.getBranches()).thenReturn(Map.of());

        pipelineWorkflowPublisher.publish("owner/materializer", Map.of("file", "hello"));

        verify(treeBuilder).add("file", "hello", false);
        verify(commitBuilder).tree("workflow-tree");
        verify(repository).createRef("refs/heads/execution", "published-commit");
    }

    @Test
    void pushesNewCommitEvenWhenWorkflowTreeIsUnchanged() throws IOException {
        preparePublication();
        GHBranch branch = mock(GHBranch.class);
        when(repository.getBranches()).thenReturn(Map.of("execution", branch));
        when(branch.getSHA1()).thenReturn("previous-commit");
        GHRef ref = mock(GHRef.class);
        when(repository.getRef("heads/execution")).thenReturn(ref);

        pipelineWorkflowPublisher.publish("owner/materializer", Map.of("file", "hello"));

        verify(treeBuilder, never()).baseTree(org.mockito.ArgumentMatchers.anyString());
        verify(commitBuilder).parent("previous-commit");
        verify(ref).updateTo("published-commit", false);
    }

    private void preparePublication() throws IOException {
        when(gitHub.getRepository("owner/materializer")).thenReturn(repository);
        when(repository.createTree()).thenReturn(treeBuilder);
        GHTree tree = mock(GHTree.class);
        when(treeBuilder.create()).thenReturn(tree);
        when(tree.getSha()).thenReturn("workflow-tree");
        when(repository.createCommit()).thenReturn(commitBuilder);
        GHCommit commit = mock(GHCommit.class);
        when(commitBuilder.create()).thenReturn(commit);
        when(commit.getSHA1()).thenReturn("published-commit");
    }
}
