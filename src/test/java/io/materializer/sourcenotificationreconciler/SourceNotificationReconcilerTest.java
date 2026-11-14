package io.materializer.sourcenotificationreconciler;

import java.util.List;

import io.materializer.notifyserviceworkflowgeneration.NotifyServiceWorkflowGenerator;
import io.materializer.notifyserviceworkflowpublishing.NotifyServiceWorkflowPublisher;
import io.materializer.shared.githubcredentialstore.GitHubCredentialStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SourceNotificationReconcilerTest {

    private final GitHubCredentialStore gitHubCredentialStore = mock(GitHubCredentialStore.class);
    private final NotifyServiceWorkflowPublisher notifyServiceWorkflowPublisher =
            mock(NotifyServiceWorkflowPublisher.class);
    private final SourceNotificationReconciler sourceNotificationReconciler = new SourceNotificationReconciler(
            gitHubCredentialStore,
            new NotifyServiceWorkflowGenerator("owner/service"),
            notifyServiceWorkflowPublisher,
            "dispatch-client",
            "dispatch-key"
    );

    @Test
    void installsCredentialsAndNotificationBeforePublishingWorkflow() {
        RepositorySnapshot source = new RepositorySnapshot("owner/new", "main", "b".repeat(40));
        sourceNotificationReconciler.reconcile(inputs(), inputs(source));

        var order = inOrder(gitHubCredentialStore, notifyServiceWorkflowPublisher);
        order.verify(gitHubCredentialStore).setVariable(eq("owner/new"), anyString(), eq("dispatch-client"));
        order.verify(gitHubCredentialStore).setSecret(eq("owner/new"), anyString(), eq("dispatch-key"));
        order.verify(notifyServiceWorkflowPublisher).publish(
                eq("owner/new"),
                eq("main"),
                anyString(),
                anyString()
        );
    }

    @Test
    void removesOnlyThisPipelinesNotificationAndCredentials() {
        RepositorySnapshot source = new RepositorySnapshot("owner/source", "main", "b".repeat(40));
        sourceNotificationReconciler.reconcile(inputs(source), inputs());

        verify(notifyServiceWorkflowPublisher).remove(eq("owner/source"), eq("main"), anyString());
        verify(gitHubCredentialStore).removeVariable(eq("owner/source"), anyString());
        verify(gitHubCredentialStore).removeSecret(eq("owner/source"), anyString());
    }

    @Test
    void retainsCredentialsWhenAnotherBranchStillUsesTheSource() {
        RepositorySnapshot main = new RepositorySnapshot("owner/source", "main", "b".repeat(40));
        RepositorySnapshot release = new RepositorySnapshot("owner/source", "release", "c".repeat(40));
        sourceNotificationReconciler.reconcile(inputs(main, release), inputs(release));

        verify(notifyServiceWorkflowPublisher).remove(eq("owner/source"), eq("main"), anyString());
        verifyNoInteractions(gitHubCredentialStore);
    }

    private LivePipelineInputs inputs(RepositorySnapshot... sources) {
        return new LivePipelineInputs(
                new RepositorySnapshot("owner/pipeline", "main", "a".repeat(40)),
                List.of(sources)
        );
    }
}
