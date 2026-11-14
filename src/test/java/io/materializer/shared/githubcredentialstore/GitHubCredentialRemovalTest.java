package io.materializer.shared.githubcredentialstore;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.github.GitHub;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubCredentialRemovalTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final HttpResponse<?> response = mock(HttpResponse.class);
    private final GitHubCredentialStore gitHubCredentialStore = new GitHubCredentialStore(
            "token",
            httpClient,
            mock(GitHub.class)
    );

    @ParameterizedTest
    @ValueSource(ints = {204, 404})
    void deletesTheNamedSecretAndAllowsRetries(int statusCode) throws IOException, InterruptedException {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(statusCode);

        gitHubCredentialStore.removeSecret("owner/source", "PIPELINE_KEY");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any());
        assertEquals("DELETE", request.getValue().method());
        assertEquals("/repos/owner/source/actions/secrets/PIPELINE_KEY", request.getValue().uri().getPath());
    }

    @Test
    void propagatesRejectedVariableRemoval() throws IOException, InterruptedException {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(403);

        assertThrows(
                IllegalStateException.class,
                () -> gitHubCredentialStore.removeVariable("owner/source", "PIPELINE_CLIENT")
        );
    }
}
