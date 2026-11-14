package io.materializer.shared.githubcredentialstore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositoryVariable;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GitHubCredentialStoreVariableTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GitHubCredentialStore gitHubCredentialStore = new GitHubCredentialStore(
            "test-token",
            mock(HttpClient.class),
            gitHub
    );

    @Test
    void recognizesPresentAndMissingVariables() throws IOException {
        when(gitHub.getRepository("owner/repository")).thenReturn(repository);
        when(repository.getVariable("EXAMPLE_ID")).thenReturn(mock(GHRepositoryVariable.class));
        assertTrue(gitHubCredentialStore.variableExists("owner/repository", "EXAMPLE_ID"));

        when(repository.getVariable("EXAMPLE_ID")).thenThrow(new GHFileNotFoundException());
        assertFalse(gitHubCredentialStore.variableExists("owner/repository", "EXAMPLE_ID"));
    }

    @Test
    void doesNotTreatAnInaccessibleRepositoryAsAMissingVariable() throws IOException {
        when(gitHub.getRepository("owner/repository")).thenThrow(new GHFileNotFoundException());

        assertThrows(
                UncheckedIOException.class,
                () -> gitHubCredentialStore.variableExists("owner/repository", "EXAMPLE_ID")
        );
    }

    @Test
    void updatesExistingVariablesByName() throws IOException {
        when(gitHub.getRepository("owner/repository")).thenReturn(repository);
        GHRepositoryVariable variable = mock(GHRepositoryVariable.class);
        GHRepositoryVariable.Setter setter = mock(GHRepositoryVariable.Setter.class);
        when(repository.getVariable("EXAMPLE_ID")).thenReturn(variable);
        when(variable.set()).thenReturn(setter);

        gitHubCredentialStore.setVariable("owner/repository", "EXAMPLE_ID", "new-value");

        verify(setter).value("new-value");
        verify(repository).getVariable("EXAMPLE_ID");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void createsMissingVariablesByName() throws IOException {
        when(gitHub.getRepository("owner/repository")).thenReturn(repository);
        when(repository.getVariable("EXAMPLE_ID")).thenThrow(new GHFileNotFoundException());

        gitHubCredentialStore.setVariable("owner/repository", "EXAMPLE_ID", "new-value");

        verify(repository).createVariable("EXAMPLE_ID", "new-value");
    }

    @Test
    void propagatesVariableLookupAndWriteFailures() throws IOException {
        when(gitHub.getRepository("owner/repository")).thenReturn(repository);
        when(repository.getVariable("EXAMPLE_ID")).thenThrow(new IOException("Denied"));

        assertThrows(
                UncheckedIOException.class,
                () -> gitHubCredentialStore.variableExists("owner/repository", "EXAMPLE_ID")
        );
        assertThrows(
                UncheckedIOException.class,
                () -> gitHubCredentialStore.setVariable("owner/repository", "EXAMPLE_ID", "new-value")
        );
    }
}
