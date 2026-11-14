package io.materializer.execution;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.materializer.shared.githubcredentialstore.GitHubCredentialStore;

class RegistrationCredentials {

    private final GitHubCredentialStore gitHubCredentialStore;
    private final String materializationDispatchClientID;
    private final String materializationDispatchPrivateKey;
    private final String materializationPublishClientID;
    private final String materializationPublishPrivateKey;

    @Inject
    RegistrationCredentials(
            GitHubCredentialStore gitHubCredentialStore,
            @Named("materializationDispatchClientID") String materializationDispatchClientID,
            @Named("materializationDispatchPrivateKey") String materializationDispatchPrivateKey,
            @Named("materializationPublishClientID") String materializationPublishClientID,
            @Named("materializationPublishPrivateKey") String materializationPublishPrivateKey
    ) {
        this.gitHubCredentialStore = gitHubCredentialStore;
        this.materializationDispatchClientID = materializationDispatchClientID;
        this.materializationDispatchPrivateKey = materializationDispatchPrivateKey;
        this.materializationPublishClientID = materializationPublishClientID;
        this.materializationPublishPrivateKey = materializationPublishPrivateKey;
    }

    void requireAbsent(String repositoryName) {
        if (gitHubCredentialStore.variableExists(repositoryName, "MATERIALIZATION_DISPATCH_CLIENT_ID")) {
            throw new IllegalStateException("Dispatcher client ID already exists: " + repositoryName);
        }
        if (gitHubCredentialStore.secretExists(repositoryName, "MATERIALIZATION_DISPATCH_PRIVATE_KEY")) {
            throw new IllegalStateException("Dispatcher private key already exists: " + repositoryName);
        }
        if (gitHubCredentialStore.variableExists(repositoryName, "MATERIALIZATION_PUBLISH_CLIENT_ID")) {
            throw new IllegalStateException("Publisher client ID already exists: " + repositoryName);
        }
        if (gitHubCredentialStore.secretExists(repositoryName, "MATERIALIZATION_PUBLISH_PRIVATE_KEY")) {
            throw new IllegalStateException("Publisher private key already exists: " + repositoryName);
        }
    }

    void provision(String repositoryName) {
        gitHubCredentialStore.setVariable(
                repositoryName,
                "MATERIALIZATION_DISPATCH_CLIENT_ID",
                materializationDispatchClientID
        );
        gitHubCredentialStore.setSecret(
                repositoryName,
                "MATERIALIZATION_DISPATCH_PRIVATE_KEY",
                materializationDispatchPrivateKey
        );
        gitHubCredentialStore.setVariable(
                repositoryName,
                "MATERIALIZATION_PUBLISH_CLIENT_ID",
                materializationPublishClientID
        );
        gitHubCredentialStore.setSecret(
                repositoryName,
                "MATERIALIZATION_PUBLISH_PRIVATE_KEY",
                materializationPublishPrivateKey
        );
    }
}
