package io.materializer.publisherconfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class PublisherConfigurator {

    private static final String CLIENT_ID_VARIABLE = "BUILD_PUBLISHER_APP_CLIENT_ID";

    private final GitHub gitHub;
    private final GitHubSecretSetter gitHubSecretSetter;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public PublisherConfigurator(
            GitHub gitHub,
            GitHubSecretSetter gitHubSecretSetter
    ) {
        this.gitHub = gitHub;
        this.gitHubSecretSetter = gitHubSecretSetter;
    }

    public void configure(
            String repositoryName,
            String buildPublisherAppClientID,
            String buildPublisherAppPrivateKey
    ) {
        try {
            GHRepository repository = gitHub.getRepository(repositoryName);
            setClientID(repository, buildPublisherAppClientID);

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not configure Publisher client ID for " + repositoryName, exception);
        }

        gitHubSecretSetter.setBuildPublisherPrivateKey(
                repositoryName,
                buildPublisherAppPrivateKey
        );
    }

    private void setClientID(GHRepository repository, String buildPublisherAppClientID) throws IOException {
        try {
            repository.getVariable(CLIENT_ID_VARIABLE).set().value(buildPublisherAppClientID);

        } catch (GHFileNotFoundException exception) {
            repository.createVariable(CLIENT_ID_VARIABLE, buildPublisherAppClientID);
        }
    }

}
