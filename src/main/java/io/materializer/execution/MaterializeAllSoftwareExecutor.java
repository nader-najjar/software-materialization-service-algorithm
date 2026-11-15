package io.materializer.execution;

import java.util.Optional;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.pipelineworkflowgeneration.PipelineWorkflowGenerator;
import io.materializer.pipelineworkflowpublishing.PipelineWorkflowPublisher;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.sourcenotificationreconciler.SourceNotificationReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaterializeAllSoftwareExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterializeAllSoftwareExecutor.class);

    private final LivePipelineInputsAdvancer livePipelineInputsAdvancer;
    private final LivePipelineInputsReconciler livePipelineInputsReconciler;
    private final SourceNotificationReconciler sourceNotificationReconciler;
    private final LivePipelineInputsStore livePipelineInputsStore;
    private final PipelineWorkflowGenerator pipelineWorkflowGenerator;
    private final PipelineWorkflowPublisher pipelineWorkflowPublisher;
    private final MaterializerRegistryStore materializerRegistryStore;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the injected dependencies")
    @Inject
    public MaterializeAllSoftwareExecutor(
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

    public void execute() {
        for (MaterializerRegistration registration : materializerRegistryStore.read().registrations()) {
            try {
                materialize(registration);
            } catch (RuntimeException exception) {
                LOGGER.error("Could not materialize {}", registration.repositoryName(), exception);
            }
        }
    }

    private void materialize(MaterializerRegistration registration) {
        String repositoryName = registration.repositoryName();
        Optional<LivePipelineInputs> advanced = livePipelineInputsAdvancer.advance(repositoryName);
        if (advanced.isEmpty()) {
            LOGGER.info("Skipped materialization for {}: no new pushes", repositoryName);
            return;
        }
        LivePipelineInputs current = livePipelineInputsReconciler.reconcile(registration, advanced.get());
        current = sourceNotificationReconciler.reconcile(advanced.get(), current);
        livePipelineInputsStore.write(current);
        if (current.declaredRepositorySnapshots().isEmpty()) {
            LOGGER.info("Pipeline {} is idle", repositoryName);
            return;
        }
        String workflowYaml = pipelineWorkflowGenerator.generate(current);
        pipelineWorkflowPublisher.publish(repositoryName, workflowYaml);
        LOGGER.info("Materialized {}", repositoryName);
    }
}
