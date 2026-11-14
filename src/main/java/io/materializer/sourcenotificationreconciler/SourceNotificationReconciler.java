package io.materializer.sourcenotificationreconciler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.notifyserviceworkflowgeneration.NotifyServiceWorkflowGenerator;
import io.materializer.notifyserviceworkflowpublishing.NotifyServiceWorkflowPublisher;
import io.materializer.shared.githubcredentialstore.GitHubCredentialStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;

public class SourceNotificationReconciler {

    private final GitHubCredentialStore gitHubCredentialStore;
    private final NotifyServiceWorkflowGenerator notifyServiceWorkflowGenerator;
    private final NotifyServiceWorkflowPublisher notifyServiceWorkflowPublisher;
    private final String materializationDispatchClientID;
    private final String materializationDispatchPrivateKey;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the injected dependencies")
    @Inject
    public SourceNotificationReconciler(
            GitHubCredentialStore gitHubCredentialStore,
            NotifyServiceWorkflowGenerator notifyServiceWorkflowGenerator,
            NotifyServiceWorkflowPublisher notifyServiceWorkflowPublisher,
            @Named("materializationDispatchClientID") String materializationDispatchClientID,
            @Named("materializationDispatchPrivateKey") String materializationDispatchPrivateKey
    ) {
        this.gitHubCredentialStore = gitHubCredentialStore;
        this.notifyServiceWorkflowGenerator = notifyServiceWorkflowGenerator;
        this.notifyServiceWorkflowPublisher = notifyServiceWorkflowPublisher;
        this.materializationDispatchClientID = materializationDispatchClientID;
        this.materializationDispatchPrivateKey = materializationDispatchPrivateKey;
    }

    public void reconcile(LivePipelineInputs previous, LivePipelineInputs current) {
        String pipelineRepository = current.materializerRepositorySnapshot().repositoryName();
        String notificationID = UUID.nameUUIDFromBytes(
                pipelineRepository.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        ).toString().replace("-", "");
        for (RepositorySnapshot source : current.declaredRepositorySnapshots()) {
            if (!containsBranch(previous.declaredRepositorySnapshots(), source)) {
                installNotification(pipelineRepository, notificationID, source);
            }
        }
        for (RepositorySnapshot source : previous.declaredRepositorySnapshots()) {
            if (!containsBranch(current.declaredRepositorySnapshots(), source)) {
                removeNotification(notificationID, source, current.declaredRepositorySnapshots());
            }
        }
    }

    private void installNotification(String pipelineRepository, String notificationID, RepositorySnapshot source) {
        String clientIDVariable = "DISPATCH_CLIENT_ID_" + notificationID;
        String privateKeySecret = "DISPATCH_PRIVATE_KEY_" + notificationID;
        String workflow = notifyServiceWorkflowGenerator.generate(
                pipelineRepository,
                source.branch(),
                clientIDVariable,
                privateKeySecret
        );
        gitHubCredentialStore.setVariable(source.repositoryName(), clientIDVariable, materializationDispatchClientID);
        gitHubCredentialStore.setSecret(source.repositoryName(), privateKeySecret, materializationDispatchPrivateKey);
        notifyServiceWorkflowPublisher.publish(
                source.repositoryName(),
                source.branch(),
                ".github/workflows/notify-service-" + notificationID + ".yml",
                workflow
        );
    }

    private void removeNotification(
            String notificationID,
            RepositorySnapshot source,
            List<RepositorySnapshot> currentSources
    ) {
        notifyServiceWorkflowPublisher.remove(
                source.repositoryName(),
                source.branch(),
                ".github/workflows/notify-service-" + notificationID + ".yml"
        );
        boolean stillUsed = currentSources.stream()
                .anyMatch(current -> current.repositoryName().equalsIgnoreCase(source.repositoryName()));
        if (!stillUsed) {
            gitHubCredentialStore.removeVariable(source.repositoryName(), "DISPATCH_CLIENT_ID_" + notificationID);
            gitHubCredentialStore.removeSecret(source.repositoryName(), "DISPATCH_PRIVATE_KEY_" + notificationID);
        }
    }

    private boolean containsBranch(List<RepositorySnapshot> snapshots, RepositorySnapshot source) {
        return snapshots.stream().anyMatch(snapshot ->
                snapshot.repositoryName().equalsIgnoreCase(source.repositoryName())
                        && snapshot.branch().equals(source.branch()));
    }
}
