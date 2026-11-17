package io.materializer.execution;

import java.util.Map;
import java.util.Optional;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.livepipelineinputsadvancer.LivePipelineInputsAdvancer;
import io.materializer.livepipelineinputsreconciler.LivePipelineInputsReconciler;
import io.materializer.pipelineworkflowgeneration.PipelineWorkflowGenerator;
import io.materializer.pipelineworkflowpublishing.PipelineWorkflowPublisher;
import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
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
    private final LivePipelineDeclarationFetcher livePipelineDeclarationFetcher;
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
            LivePipelineDeclarationFetcher livePipelineDeclarationFetcher,
            PipelineWorkflowGenerator pipelineWorkflowGenerator,
            PipelineWorkflowPublisher pipelineWorkflowPublisher,
            MaterializerRegistryStore materializerRegistryStore
    ) {
        this.livePipelineInputsAdvancer = livePipelineInputsAdvancer;
        this.livePipelineInputsReconciler = livePipelineInputsReconciler;
        this.sourceNotificationReconciler = sourceNotificationReconciler;
        this.livePipelineInputsStore = livePipelineInputsStore;
        this.livePipelineDeclarationFetcher = livePipelineDeclarationFetcher;
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
        LivePipelineInputs advancedInputs = advanced.get();
        LivePipelineDeclaration declaration = livePipelineDeclarationFetcher.fetchAndValidate(
                advancedInputs.materializerRepositorySnapshot()
        );
        LivePipelineInputs current = livePipelineInputsReconciler.reconcile(registration, advancedInputs, declaration);
        current = sourceNotificationReconciler.reconcile(advancedInputs, current);
        livePipelineInputsStore.write(current);
        if (current.declaredRepositorySnapshots().isEmpty()) {
            LOGGER.info("Pipeline {} is idle", repositoryName);
            return;
        }
        Map<String, String> files = pipelineWorkflowGenerator.generate(declaration, current);
        pipelineWorkflowPublisher.publish(repositoryName, files);
        LOGGER.info("Materialized {}", repositoryName);
    }
}
