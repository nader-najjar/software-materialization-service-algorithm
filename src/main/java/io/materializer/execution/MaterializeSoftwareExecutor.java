package io.materializer.execution;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.execution.models.MaterializeSoftwareApplicationInput;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.pipelineworkflowgeneration.PipelineWorkflowGenerator;
import io.materializer.pipelineworkflowpublishing.PipelineWorkflowPublisher;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.utilities.JsonSanitizer;
import io.materializer.sourcenotificationreconciler.SourceNotificationReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaterializeSoftwareExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterializeSoftwareExecutor.class);

    private final LivePipelineInputsAdvancer livePipelineInputsAdvancer;
    private final LivePipelineInputsReconciler livePipelineInputsReconciler;
    private final SourceNotificationReconciler sourceNotificationReconciler;
    private final LivePipelineInputsStore livePipelineInputsStore;
    private final PipelineWorkflowGenerator pipelineWorkflowGenerator;
    private final PipelineWorkflowPublisher pipelineWorkflowPublisher;
    private final MaterializerRegistryStore materializerRegistryStore;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the injected dependencies")
    @Inject
    public MaterializeSoftwareExecutor(
            LivePipelineInputsAdvancer livePipelineInputsAdvancer,
            LivePipelineInputsReconciler livePipelineInputsReconciler,
            SourceNotificationReconciler sourceNotificationReconciler,
            LivePipelineInputsStore livePipelineInputsStore,
            PipelineWorkflowGenerator pipelineWorkflowGenerator,
            PipelineWorkflowPublisher pipelineWorkflowPublisher,
            MaterializerRegistryStore materializerRegistryStore
    ) {
        this.livePipelineInputsAdvancer = livePipelineInputsAdvancer;
        this.livePipelineInputsReconciler = livePipelineInputsReconciler;
        this.sourceNotificationReconciler = sourceNotificationReconciler;
        this.livePipelineInputsStore = livePipelineInputsStore;
        this.pipelineWorkflowGenerator = pipelineWorkflowGenerator;
        this.pipelineWorkflowPublisher = pipelineWorkflowPublisher;
        this.materializerRegistryStore = materializerRegistryStore;
    }

    public void execute(String json) {
        MaterializeSoftwareApplicationInput input = JsonSanitizer.parseAndValidate(
                json,
                MaterializeSoftwareApplicationInput.class
        );
        boolean registered = materializerRegistryStore.read().registrations().stream()
                .anyMatch(registration -> registration.repositoryName().equalsIgnoreCase(input.pipelineRepository()));
        if (!registered) {
            throw new IllegalStateException("Pipeline is not registered: " + input.pipelineRepository());
        }
        LivePipelineInputs inputs = livePipelineInputsAdvancer.advance(
                input.pipelineRepository(),
                input.pushEvent()
        );
        LivePipelineInputs reconciledInputs = livePipelineInputsReconciler.reconcile(inputs);
        sourceNotificationReconciler.reconcile(inputs, reconciledInputs);
        livePipelineInputsStore.write(reconciledInputs);
        String workflowYaml = pipelineWorkflowGenerator.generate(reconciledInputs);
        pipelineWorkflowPublisher.publish(input.pipelineRepository(), workflowYaml);
        LOGGER.info("Materialized {}", input.pipelineRepository());
    }
}
