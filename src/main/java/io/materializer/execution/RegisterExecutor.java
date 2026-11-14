package io.materializer.execution;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.execution.models.RegisterApplicationInput;
import io.materializer.notifyserviceworkflowgeneration.NotifyServiceWorkflowGenerator;
import io.materializer.notifyserviceworkflowpublishing.NotifyServiceWorkflowPublisher;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import io.materializer.shared.utilities.JsonSanitizer;

public class RegisterExecutor {

    private final MaterializerRegistryStore materializerRegistryStore;
    private final LivePipelineInputsStore livePipelineInputsStore;
    private final RegistrationCredentials registrationCredentials;
    private final NotifyServiceWorkflowGenerator notifyServiceWorkflowGenerator;
    private final NotifyServiceWorkflowPublisher notifyServiceWorkflowPublisher;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the injected dependencies")
    @Inject
    public RegisterExecutor(
            MaterializerRegistryStore materializerRegistryStore,
            LivePipelineInputsStore livePipelineInputsStore,
            RegistrationCredentials registrationCredentials,
            NotifyServiceWorkflowGenerator notifyServiceWorkflowGenerator,
            NotifyServiceWorkflowPublisher notifyServiceWorkflowPublisher
    ) {
        this.materializerRegistryStore = materializerRegistryStore;
        this.livePipelineInputsStore = livePipelineInputsStore;
        this.registrationCredentials = registrationCredentials;
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

        registrationCredentials.requireAbsent(repositoryName);

        String branch = notifyServiceWorkflowPublisher.defaultBranch(repositoryName);
        String workflow = notifyServiceWorkflowGenerator.generate(
                repositoryName,
                branch
        );

        registrationCredentials.provision(repositoryName);
        notifyServiceWorkflowPublisher.publish(
                repositoryName,
                branch,
                ".github/workflows/notify-service.yml",
                workflow
        );
        materializerRegistryStore.addRegistration(snapshot, repositoryName);
    }

}
