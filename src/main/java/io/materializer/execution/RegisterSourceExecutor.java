package io.materializer.execution;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.execution.models.RegisterSourceApplicationInput;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.utilities.JsonSanitizer;

public class RegisterSourceExecutor {

    private final MaterializerRegistryStore materializerRegistryStore;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared registry store")
    @Inject
    public RegisterSourceExecutor(MaterializerRegistryStore materializerRegistryStore) {
        this.materializerRegistryStore = materializerRegistryStore;
    }

    public void execute(String json) {
        RegisterSourceApplicationInput input = JsonSanitizer.parseAndValidate(
                json, RegisterSourceApplicationInput.class
        );
        materializerRegistryStore.registerSource(input.materializerRepository(), input.sourceRepository());
    }
}
