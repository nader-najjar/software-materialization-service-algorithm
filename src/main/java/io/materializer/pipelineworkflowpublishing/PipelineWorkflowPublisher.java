package io.materializer.pipelineworkflowpublishing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.GitHub;

public final class PipelineWorkflowPublisher {

    private final GitHub gitHub;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public PipelineWorkflowPublisher(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    public void publish(String materializerRepositoryName, Map<String, String> files) {
        try {
            GHRepository repository = gitHub.getRepository(materializerRepositoryName);
            GHBranch executionBranch = repository.getBranches().get("execution");
            GHTreeBuilder treeBuilder = repository.createTree();
            GHCommitBuilder commitBuilder = repository.createCommit();

            if (executionBranch != null) {
                String parentCommitSHA = executionBranch.getSHA1();
                commitBuilder.parent(parentCommitSHA);
            }

            for (Map.Entry<String, String> file : files.entrySet()) {
                treeBuilder.add(file.getKey(), file.getValue(), false);
            }
            GHTree tree = treeBuilder.create();
            GHCommit commit = commitBuilder.message("Publish materialization workflow").tree(tree.getSha()).create();

            if (executionBranch == null) {
                repository.createRef("refs/heads/execution", commit.getSHA1());

            } else {
                repository.getRef("heads/execution").updateTo(commit.getSHA1(), false);
            }

        } catch (IOException exception) {
            String message = "Could not publish workflow to %s on execution".formatted(materializerRepositoryName);
            throw new UncheckedIOException(message, exception);
        }
    }
}
