package io.materializer.composition;
import java.io.IOException;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.materializer.shared.models.GitHubToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class GitHubClientsModule extends AbstractModule {

    @Provides
    @Singleton
    GitHub provideGitHubClient(GitHubToken gitHubToken) throws IOException {
        return new GitHubBuilder().withOAuthToken(gitHubToken.githubToken()).build();
    }
}
