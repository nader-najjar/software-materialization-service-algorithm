package io.materializer.shared.materializerregistrystore;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.shared.utilities.JsonSanitizer;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public class MaterializerRegistryStore {

    private final GitHub gitHub;
    private final String registryRepository;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public MaterializerRegistryStore(
            GitHub gitHub,
            @Named("materializerRegistryRepository") String materializerRegistryRepository
    ) {
        this.gitHub = gitHub;
        this.registryRepository = materializerRegistryRepository;
    }

    public MaterializerRegistry read() {
        return readSnapshot().registry();
    }

    public MaterializerRegistrySnapshot readSnapshot() {
        GHContent content = fetchContent();
        String json = readContent(content);
        MaterializerRegistry registry = JsonSanitizer.parseAndValidate(json, MaterializerRegistry.class);
        return new MaterializerRegistrySnapshot(registry, content.getSha());
    }

    public void addRegistration(MaterializerRegistrySnapshot snapshot, MaterializerRegistration registration) {
        List<MaterializerRegistration> registrations = new ArrayList<>(snapshot.registry().registrations());
        registrations.add(registration);
        write(new MaterializerRegistry(registrations), snapshot.fileSHA(), "Register " + registration.repositoryName());
    }

    public void registerSource(String materializerRepository, String sourceRepository) {
        MaterializerRegistrySnapshot snapshot = readSnapshot();
        List<MaterializerRegistration> registrations = new ArrayList<>(snapshot.registry().registrations());
        MaterializerRegistration registration = registrations.stream()
                .filter(entry -> entry.repositoryName().equalsIgnoreCase(materializerRepository))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("Materializer is not registered: " + materializerRepository)
                );
        if (registration.sourceRepositories().stream().anyMatch(sourceRepository::equalsIgnoreCase)) {
            throw new IllegalStateException(
                    "Repository " + sourceRepository + " is already registered for " + materializerRepository
            );
        }
        List<String> sources = new ArrayList<>(registration.sourceRepositories());
        sources.add(sourceRepository);
        registrations.set(
                registrations.indexOf(registration),
                new MaterializerRegistration(registration.repositoryName(), sources)
        );
        write(
                new MaterializerRegistry(registrations),
                snapshot.fileSHA(),
                "Register " + sourceRepository + " for " + materializerRepository
        );
    }

    private void write(MaterializerRegistry registry, String fileSHA, String commitMessage) {
        try {
            byte[] json = JsonMapperUtility.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(registry);
            gitHub.getRepository(registryRepository).createContent()
                    .path("materializer-registry.json")
                    .sha(fileSHA)
                    .content(json)
                    .message(commitMessage)
                    .commit();

        } catch (IOException exception) {
            String message = "Could not update materializer registry in " + registryRepository;
            throw new UncheckedIOException(message, exception);
        }
    }

    private GHContent fetchContent() {
        try {
            GHRepository repository = gitHub.getRepository(registryRepository);
            return repository.getFileContent("materializer-registry.json");

        } catch (IOException exception) {
            String message = "Could not read materializer registry from %s".formatted(registryRepository);

            throw new UncheckedIOException(message, exception);
        }
    }

    private String readContent(GHContent registryFileContent) {
        try (InputStream content = registryFileContent.read()) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException exception) {
            String message = "Could not read materializer registry from " + registryRepository;
            throw new UncheckedIOException(message, exception);
        }
    }
}
