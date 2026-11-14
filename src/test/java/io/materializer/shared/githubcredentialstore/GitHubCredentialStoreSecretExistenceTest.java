package io.materializer.shared.githubcredentialstore;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubCredentialStoreSecretExistenceTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final GitHubCredentialStore gitHubCredentialStore = new GitHubCredentialStore(
            "test-token",
            httpClient,
            mock(GitHub.class)
    );

    @ParameterizedTest
    @ValueSource(ints = {200, 404})
    void checksNamedSecretWithoutWriting(int statusCode) throws IOException, InterruptedException {
        respond("secrets", statusCode);

        assertEquals(statusCode == 200, gitHubCredentialStore.secretExists("owner/materializer", "EXAMPLE_SECRET"));

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requests.capture(), any());
        HttpRequest request = requests.getValue();
        assertEquals(
                "https://api.github.com/repos/owner/materializer/actions/secrets/EXAMPLE_SECRET",
                request.uri().toString()
        );
        assertEquals("GET", request.method());
        assertEquals("Bearer test-token", request.headers().firstValue("Authorization").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void rejectsFailedLookups(int statusCode) throws IOException, InterruptedException {
        respond("secrets", statusCode);

        assertThrows(
                IllegalStateException.class,
                () -> gitHubCredentialStore.secretExists("owner/materializer", "EXAMPLE_SECRET")
        );
    }

    @Test
    void propagatesTransportFailure() throws IOException, InterruptedException {
        when(httpClient.<Void>send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Offline"));

        assertThrows(
                UncheckedIOException.class,
                () -> gitHubCredentialStore.secretExists("owner/materializer", "EXAMPLE_SECRET")
        );
    }

    @Test
    void preservesInterruption() throws IOException, InterruptedException {
        when(httpClient.<Void>send(any(HttpRequest.class), any()))
                .thenThrow(new InterruptedException());
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> gitHubCredentialStore.secretExists("owner/materializer", "EXAMPLE_SECRET")
            );
            assertTrue(Thread.currentThread().isInterrupted());

        } finally {
            Thread.interrupted();
        }
    }

    @SuppressWarnings("unchecked")
    private void respond(String category, int statusCode) throws IOException, InterruptedException {
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(httpClient.<Void>send(
                argThat(request -> request != null && request.uri().getPath().contains("/" + category + "/")),
                any()
        )).thenReturn(response);
    }
}
