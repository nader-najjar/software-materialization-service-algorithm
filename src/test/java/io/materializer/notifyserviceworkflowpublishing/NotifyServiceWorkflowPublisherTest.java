package io.materializer.notifyserviceworkflowpublishing;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHContentBuilder;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifyServiceWorkflowPublisherTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GHContentBuilder contentBuilder = mock(GHContentBuilder.class, RETURNS_SELF);
    private final NotifyServiceWorkflowPublisher publisher = new NotifyServiceWorkflowPublisher(gitHub);

    @Test
    void createsNotificationOnTheSelectedBranch() throws IOException {
        when(gitHub.getRepository("owner/pipeline")).thenReturn(repository);
        when(repository.getDefaultBranch()).thenReturn("development");
        when(repository.createContent()).thenReturn(contentBuilder);

        assertEquals("development", publisher.defaultBranch("owner/pipeline"));
        publisher.publish("owner/pipeline", "development", ".github/workflows/notify-service.yml", "workflow");

        verify(contentBuilder).path(".github/workflows/notify-service.yml");
        verify(contentBuilder).branch("development");
        verify(contentBuilder).content("workflow");
        verify(contentBuilder).commit();
    }

    @Test
    void failsWhenPublicationIsRejected() throws IOException {
        when(gitHub.getRepository("owner/pipeline")).thenReturn(repository);
        when(repository.createContent()).thenReturn(contentBuilder);
        when(contentBuilder.commit()).thenThrow(new IOException("Rejected"));

        assertThrows(
                UncheckedIOException.class,
                () -> publisher.publish("owner/pipeline", "main", "notification.yml", "workflow")
        );
    }

    @Test
    void overwritesExistingWorkflowWithoutComparingContents() throws IOException {
        GHContent content = mock(GHContent.class);
        when(gitHub.getRepository("owner/source")).thenReturn(repository);
        when(repository.getFileContent("notification.yml", "main")).thenReturn(content);
        when(content.getSha()).thenReturn("existing-sha");
        when(repository.createContent()).thenReturn(contentBuilder);

        publisher.publish("owner/source", "main", "notification.yml", "workflow");

        verify(content, never()).read();
        verify(contentBuilder).sha("existing-sha");
        verify(contentBuilder).content("workflow");
        verify(contentBuilder).commit();
    }

    @Test
    void publishesMissingWorkflow() throws IOException {
        when(gitHub.getRepository("owner/source")).thenReturn(repository);
        when(repository.getFileContent("notification.yml", "main")).thenThrow(new GHFileNotFoundException());
        when(repository.createContent()).thenReturn(contentBuilder);

        publisher.publish("owner/source", "main", "notification.yml", "workflow");

        verify(contentBuilder).path("notification.yml");
        verify(contentBuilder).branch("main");
        verify(contentBuilder).commit();
    }

    @Test
    void removesOnlyTheSpecifiedFileAndBranch() throws IOException {
        GHContent content = mock(GHContent.class);
        when(gitHub.getRepository("owner/source")).thenReturn(repository);
        when(repository.getFileContent("notification.yml", "release")).thenReturn(content);

        publisher.remove("owner/source", "release", "notification.yml");

        verify(content).delete("Remove service notification workflow", "release");
    }

}
