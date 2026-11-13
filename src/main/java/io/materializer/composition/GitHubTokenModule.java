package io.materializer.composition;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.materializer.shared.models.GitHubToken;
import io.materializer.shared.utilities.HibernateValidatorUtility;

public class GitHubTokenModule extends AbstractModule {

    @Provides
    @Singleton
    GitHubToken provideGitHubToken() {
        GitHubToken gitHubToken = new GitHubToken(System.getenv("GITHUB_TOKEN"));
        HibernateValidatorUtility.validate(gitHubToken);
        return gitHubToken;
    }
}
