package io.materializer.shared.githubcredentialstore;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class GitHubCredentialStore {

    private final String githubToken;
    private final HttpClient httpClient;
    private final GitHub gitHub;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public GitHubCredentialStore(
            @Named("githubToken") String gitHubToken,
            HttpClient httpClient,
            GitHub gitHub
    ) {
        this.githubToken = gitHubToken;
        this.httpClient = httpClient;
        this.gitHub = gitHub;
    }

    public boolean secretExists(String repositoryName, String name) {
        URI uri = URI.create("https://api.github.com/repos/%s/actions/secrets/%s".formatted(repositoryName, name));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github+json")
                .build();
        int statusCode = send(request);
        if (statusCode == 200) { return true; }
        if (statusCode == 404) { return false; }
        throw new IllegalStateException("Could not check repository secret %s: HTTP %d".formatted(name, statusCode));
    }

    public void setSecret(String repositoryName, String name, String value) {
        try {
            writeSecret(repositoryName, name, value);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while setting repository secret");

        } catch (IOException exception) {
            throw new IllegalStateException("Could not set repository secret with GitHub CLI");
        }
    }

    public boolean variableExists(String repositoryName, String name) {
        GHRepository repository;
        try {
            repository = gitHub.getRepository(repositoryName);

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not access repository " + repositoryName, exception);
        }
        try {
            repository.getVariable(name);
            return true;

        } catch (GHFileNotFoundException exception) {
            return false;

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not check repository variable " + name, exception);
        }
    }

    public void setVariable(String repositoryName, String name, String value) {
        try {
            GHRepository repository = gitHub.getRepository(repositoryName);
            writeVariable(repository, name, value);

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not set repository variable " + name, exception);
        }
    }

    public void removeVariable(String repositoryName, String name) {
        remove(repositoryName, "variables", name);
    }

    public void removeSecret(String repositoryName, String name) {
        remove(repositoryName, "secrets", name);
    }

    private void remove(String repositoryName, String kind, String name) {
        URI uri = URI.create("https://api.github.com/repos/%s/actions/%s/%s".formatted(repositoryName, kind, name));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github+json")
                .DELETE()
                .build();
        int statusCode = send(request);
        if (statusCode != 204 && statusCode != 404) {
            throw new IllegalStateException("Could not remove repository credential: HTTP " + statusCode);
        }
    }

    private void writeVariable(GHRepository repository, String name, String value) throws IOException {
        try {
            repository.getVariable(name).set().value(value);

        } catch (GHFileNotFoundException exception) {
            repository.createVariable(name, value);
        }
    }

    private void writeSecret(
            String repositoryName,
            String name,
            String value
    ) throws IOException, InterruptedException {
        Path configurationDirectory = Files.createTempDirectory("materializer-gh-");
        try {
            ProcessBuilder builder = createProcessBuilder(repositoryName, name, configurationDirectory);
            run(builder.start(), value);

        } finally {
            Files.deleteIfExists(configurationDirectory);
        }
    }

    private ProcessBuilder createProcessBuilder(String repositoryName, String name, Path configurationDirectory) {
        ProcessBuilder builder = new ProcessBuilder(
                "gh",
                "secret",
                "set",
                name,
                "--repo",
                "github.com/" + repositoryName,
                "--app",
                "actions"
        );
        configureEnvironment(builder.environment(), configurationDirectory);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        return builder;
    }

    private void configureEnvironment(Map<String, String> environment, Path configurationDirectory) {
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) {
            environment.put("PATH", path);
        }
        environment.put("GH_TOKEN", githubToken);
        environment.put("GH_HOST", "github.com");
        environment.put("GH_PROMPT_DISABLED", "1");
        environment.put("GH_NO_UPDATE_NOTIFIER", "1");
        environment.put("GH_NO_EXTENSION_UPDATE_NOTIFIER", "1");
        environment.put("GH_CONFIG_DIR", configurationDirectory.toString());
        environment.put("HOME", configurationDirectory.toString());
    }

    private void run(Process process, String privateKey) throws IOException, InterruptedException {
        int exitCode;
        try {
            exitCode = writeSecretAndWait(process, privateKey);

        } finally {
            process.destroyForcibly();
        }
        if (exitCode != 0) {
            throw new IllegalStateException("GitHub CLI secret setting failed with exit code " + exitCode);
        }
    }

    private int writeSecretAndWait(Process process, String privateKey) throws IOException, InterruptedException {
        try (OutputStream input = process.getOutputStream()) {
            input.write(privateKey.getBytes(StandardCharsets.UTF_8));
        }
        return process.waitFor();
    }

    private int send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not check repository secret", exception);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking repository secret", exception);
        }
    }
}
