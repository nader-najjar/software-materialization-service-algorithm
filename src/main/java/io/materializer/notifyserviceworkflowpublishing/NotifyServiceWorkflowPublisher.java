package io.materializer.notifyserviceworkflowpublishing;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHContentBuilder;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class NotifyServiceWorkflowPublisher {

    private final GitHub gitHub;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public NotifyServiceWorkflowPublisher(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    public String defaultBranch(String repositoryName) {
        try {
            return gitHub.getRepository(repositoryName).getDefaultBranch();

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read repository default branch: " + repositoryName, exception);
        }
    }

    public void publish(String repositoryName, String branch, String path, String workflowYaml) {
        try {
            GHRepository repository = gitHub.getRepository(repositoryName);
            GHContent existing = findContent(repository, branch, path);
            GHContentBuilder builder = repository.createContent().path(path).branch(branch)
                    .content(workflowYaml).message("Update service notification workflow");
            if (existing != null) { builder.sha(existing.getSha()); }
            builder.commit();

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not update notify service workflow: " + repositoryName, exception);
        }
    }

    public void remove(String repositoryName, String branch, String path) {
        try {
            GHRepository repository = gitHub.getRepository(repositoryName);
            GHContent existing = findContent(repository, branch, path);
            if (existing != null) { existing.delete("Remove service notification workflow", branch); }

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not remove notify service workflow: " + repositoryName, exception);
        }
    }

    private GHContent findContent(GHRepository repository, String branch, String path) throws IOException {
        try {
            return repository.getFileContent(path, branch);

        } catch (GHFileNotFoundException exception) {
            return null;
        }
    }

}
