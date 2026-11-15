package io.materializer.composition;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.materializer.composition.models.PossibleEnvironmentVariables;
import io.materializer.shared.utilities.HibernateValidatorUtility;

public class SourceRegistrationEnvironmentModule extends AbstractModule {

    private final PossibleEnvironmentVariables environmentVariables = new PossibleEnvironmentVariables(
            System.getenv("GITHUB_TOKEN"),
            System.getenv("MATERIALIZER_REGISTRY_REPOSITORY"),
            null, null, null, null
    );

    @Provides
    @Singleton
    @Named("githubToken")
    String provideGithubToken() {
        HibernateValidatorUtility.validateProperties(environmentVariables, "githubToken");
        return environmentVariables.githubToken();
    }

    @Provides
    @Singleton
    @Named("materializerRegistryRepository")
    String provideMaterializerRegistryRepository() {
        HibernateValidatorUtility.validateProperties(environmentVariables, "materializerRegistryRepository");
        return environmentVariables.materializerRegistryRepository();
    }
}
