package io.materializer.execution;
import java.io.IOException;
import java.io.UncheckedIOException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.execution.models.RegisterApplicationInput;
import io.materializer.notifyserviceworkflowgeneration.NotifyServiceWorkflowGenerator;
import io.materializer.notifyserviceworkflowpublishing.NotifyServiceWorkflowPublisher;
import io.materializer.shared.githubcredentialstore.GitHubCredentialStore;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import io.materializer.shared.utilities.JsonSanitizer;
import org.kohsuke.github.GitHub;

public class RegisterExecutor {

    private final GitHub gitHub;
    private final MaterializerRegistryStore materializerRegistryStore;
    private final LivePipelineInputsStore livePipelineInputsStore;
    private final GitHubCredentialStore gitHubCredentialStore;
    private final String materializationDispatchClientID;
    private final String materializationDispatchPrivateKey;
    private final NotifyServiceWorkflowGenerator notifyServiceWorkflowGenerator;
    private final NotifyServiceWorkflowPublisher notifyServiceWorkflowPublisher;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the injected dependencies")
    @Inject
    public RegisterExecutor(
            GitHub gitHub,
            MaterializerRegistryStore materializerRegistryStore,
            LivePipelineInputsStore livePipelineInputsStore,
            GitHubCredentialStore gitHubCredentialStore,
            @Named("materializationDispatchClientID") String materializationDispatchClientID,
            @Named("materializationDispatchPrivateKey") String materializationDispatchPrivateKey,
            NotifyServiceWorkflowGenerator notifyServiceWorkflowGenerator,
            NotifyServiceWorkflowPublisher notifyServiceWorkflowPublisher
    ) {
        this.gitHub = gitHub;
        this.materializerRegistryStore = materializerRegistryStore;
        this.livePipelineInputsStore = livePipelineInputsStore;
        this.gitHubCredentialStore = gitHubCredentialStore;
        this.materializationDispatchClientID = materializationDispatchClientID;
        this.materializationDispatchPrivateKey = materializationDispatchPrivateKey;
        this.notifyServiceWorkflowGenerator = notifyServiceWorkflowGenerator;
        this.notifyServiceWorkflowPublisher = notifyServiceWorkflowPublisher;
    }

    public void execute(String json) {
        RegisterApplicationInput input = JsonSanitizer.parseAndValidate(json, RegisterApplicationInput.class);

        String repositoryName = input.repositoryName();
        MaterializerRegistrySnapshot snapshot = materializerRegistryStore.readSnapshot();

        for (MaterializerRegistration registration : snapshot.registry().registrations()) {
            if (registration.repositoryName().equalsIgnoreCase(repositoryName)) {
                throw new IllegalStateException("Materializer is already registered: " + repositoryName);
            }
        }

        if (livePipelineInputsStore.read(repositoryName).isPresent()) {
            throw new IllegalStateException("Pipeline inputs already exist: " + repositoryName);
        }

        requireDispatchCredentialsAbsent(repositoryName);

        String branch = getDefaultBranch(repositoryName);
        String workflow = notifyServiceWorkflowGenerator.generate(branch);

        provisionDispatchCredentials(repositoryName);
        notifyServiceWorkflowPublisher.publish(
                repositoryName,
                branch,
                ".github/workflows/notify-service.yml",
                workflow
        );
        MaterializerRegistration registration = new MaterializerRegistration(
                repositoryName,
                input.sourceRepositories()
        );
        materializerRegistryStore.addRegistration(snapshot, registration);
    }

    private void requireDispatchCredentialsAbsent(String repositoryName) {
        if (gitHubCredentialStore.variableExists(repositoryName, "MATERIALIZATION_DISPATCH_CLIENT_ID")) {
            throw new IllegalStateException("Dispatcher client ID already exists: " + repositoryName);
        }
        if (gitHubCredentialStore.secretExists(repositoryName, "MATERIALIZATION_DISPATCH_PRIVATE_KEY")) {
            throw new IllegalStateException("Dispatcher private key already exists: " + repositoryName);
        }
    }

    private String getDefaultBranch(String repositoryName) {
        try {
            return gitHub.getRepository(repositoryName).getDefaultBranch();

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read repository default branch: " + repositoryName, exception);
        }
    }

    private void provisionDispatchCredentials(String repositoryName) {
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
    }
}
