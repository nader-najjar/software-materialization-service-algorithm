package io.materializer.sourcenotificationreconciler;

import java.util.List;

import io.materializer.notifyserviceworkflowgeneration.NotifyServiceWorkflowGenerator;
import io.materializer.notifyserviceworkflowpublishing.NotifyServiceWorkflowPublisher;
import io.materializer.shared.githubcredentialstore.GitHubCredentialStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.repositorypushhistory.RepositoryPushHistory;
import io.materializer.shared.repositorypushhistory.models.RepositoryPush;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SourceNotificationReconcilerTest {

    private final RepositoryPushHistory history = mock(RepositoryPushHistory.class);
    private final GitHubCredentialStore gitHubCredentialStore = mock(GitHubCredentialStore.class);
    private final NotifyServiceWorkflowPublisher notifyServiceWorkflowPublisher =
            mock(NotifyServiceWorkflowPublisher.class);
    private final SourceNotificationReconciler sourceNotificationReconciler = new SourceNotificationReconciler(
            history,
            gitHubCredentialStore,
            new NotifyServiceWorkflowGenerator("owner/service"),
            notifyServiceWorkflowPublisher,
            "dispatch-client",
            "dispatch-key"
    );

    @Test
    void installsCredentialsAndNotificationBeforePublishingWorkflow() {
        RepositorySnapshot source = new RepositorySnapshot("owner/new", "main", "b".repeat(40), 1L);
        String notificationSHA = "c".repeat(40);
        when(notifyServiceWorkflowPublisher.publish(eq("owner/new"), eq("main"), anyString(), anyString()))
                .thenReturn(notificationSHA);
        when(history.since(source)).thenReturn(List.of(
                new RepositoryPush(2L, source.commitSHA(), notificationSHA, "2026-11-15T00:00:00Z"),
                new RepositoryPush(3L, notificationSHA, "d".repeat(40), "2026-11-15T00:00:01Z")
        ));
        LivePipelineInputs reconciled = sourceNotificationReconciler.reconcile(inputs(), inputs(source));
        assertEquals(inputs(new RepositorySnapshot("owner/new", "main", notificationSHA, 2L)), reconciled);

        InOrder order = inOrder(gitHubCredentialStore, notifyServiceWorkflowPublisher);
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
    void keepsTheKnownPinWhenTheNotificationPushIsNotVisibleYet() {
        RepositorySnapshot source = new RepositorySnapshot("owner/new", "main", "b".repeat(40), 1L);
        when(notifyServiceWorkflowPublisher.publish(eq("owner/new"), eq("main"), anyString(), anyString()))
                .thenReturn("c".repeat(40));
        assertEquals(inputs(source), sourceNotificationReconciler.reconcile(inputs(), inputs(source)));
    }

    @Test
    void removesOnlyThisPipelinesNotificationAndCredentials() {
        RepositorySnapshot source = new RepositorySnapshot("owner/source", "main", "b".repeat(40), 1L);
        sourceNotificationReconciler.reconcile(inputs(source), inputs());

        verify(notifyServiceWorkflowPublisher).remove(eq("owner/source"), eq("main"), anyString());
        verify(gitHubCredentialStore).removeVariable(eq("owner/source"), anyString());
        verify(gitHubCredentialStore).removeSecret(eq("owner/source"), anyString());
    }

    @Test
    void retainsCredentialsWhenAnotherBranchStillUsesTheSource() {
        RepositorySnapshot main = new RepositorySnapshot("owner/source", "main", "b".repeat(40), 1L);
        RepositorySnapshot release = new RepositorySnapshot("owner/source", "release", "c".repeat(40), 1L);
        sourceNotificationReconciler.reconcile(inputs(main, release), inputs(release));

        verify(notifyServiceWorkflowPublisher).remove(eq("owner/source"), eq("main"), anyString());
        verifyNoInteractions(gitHubCredentialStore);
    }

    private LivePipelineInputs inputs(RepositorySnapshot... sources) {
        return new LivePipelineInputs(
                new RepositorySnapshot("owner/pipeline", "main", "a".repeat(40), 1L),
                List.of(sources)
        );
    }
}
