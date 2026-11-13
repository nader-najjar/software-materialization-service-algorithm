package io.materializer.execution;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.execution.models.RegisterApplicationInput;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import io.materializer.shared.utilities.JsonSanitizer;

public class RegisterExecutor {


    private final MaterializerRegistryStore materializerRegistryStore;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the injected dependencies")
    @Inject
    public RegisterExecutor(MaterializerRegistryStore materializerRegistryStore) {
        this.materializerRegistryStore = materializerRegistryStore;
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

    }

}
