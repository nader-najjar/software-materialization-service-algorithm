package io.materializer.composition;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.materializer.composition.models.PossibleEnvironmentVariables;
import io.materializer.shared.utilities.HibernateValidatorUtility;

public class MaterializationEnvironmentModule extends AbstractModule {

    private final PossibleEnvironmentVariables environmentVariables = new PossibleEnvironmentVariables(
            System.getenv("GITHUB_TOKEN"),
            System.getenv("MATERIALIZER_REGISTRY_REPOSITORY"),
            System.getenv("LIVE_PIPELINE_INPUTS_STORE_REPOSITORY"),
            System.getenv("MATERIALIZATION_SERVICE_REPOSITORY"),
            System.getenv("MATERIALIZATION_DISPATCH_CLIENT_ID"),
            System.getenv("MATERIALIZATION_DISPATCH_PRIVATE_KEY"),
            null,
            null
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

    @Provides
    @Singleton
    @Named("pipelineInputsRepository")
    String providePipelineInputsRepository() {
        HibernateValidatorUtility.validateProperties(environmentVariables, "pipelineInputsRepository");
        return environmentVariables.pipelineInputsRepository();
    }

    @Provides
    @Singleton
    @Named("materializationServiceRepository")
    String provideMaterializationServiceRepository() {
        HibernateValidatorUtility.validateProperties(environmentVariables, "materializationServiceRepository");
        return environmentVariables.materializationServiceRepository();
    }

    @Provides
    @Singleton
    @Named("materializationDispatchClientID")
    String provideMaterializationDispatchClientID() {
        HibernateValidatorUtility.validateProperties(environmentVariables, "materializationDispatchClientID");
        return environmentVariables.materializationDispatchClientID();
    }

    @Provides
    @Singleton
    @Named("materializationDispatchPrivateKey")
    String provideMaterializationDispatchPrivateKey() {
        HibernateValidatorUtility.validateProperties(environmentVariables, "materializationDispatchPrivateKey");
        return environmentVariables.materializationDispatchPrivateKey();
    }
}
