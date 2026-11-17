package io.materializer.pipelineworkflowgeneration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.shared.models.RepositorySnapshot;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;

public class ActionBundleReader {

    private final GitHub gitHub;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared GitHub client")
    @Inject
    public ActionBundleReader(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    public Map<String, String> readFiles(
            RepositorySnapshot materializerSnapshot,
            String actionBundlePath
    ) {
        String prefix = bundlePrefix(actionBundlePath);
        Map<String, String> files = new LinkedHashMap<>();
        for (String path : listFiles(materializerSnapshot, actionBundlePath)) {
            files.put(path.substring(prefix.length()), readFile(materializerSnapshot, path));
        }
        return files;
    }

    public List<String> listFiles(
            RepositorySnapshot materializerSnapshot,
            String actionBundlePath
    ) {
        GHTree tree = readTree(materializerSnapshot);
        String prefix = bundlePrefix(actionBundlePath);
        List<String> files = tree.getTree().stream()
                .filter(entry -> "blob".equals(entry.getType()))
                .map(GHTreeEntry::getPath)
                .filter(path -> path.startsWith(prefix))
                .toList();
        if (files.isEmpty()) {
            throw new IllegalStateException("No files found in action bundle: " + actionBundlePath);
        }
        if (!files.contains(prefix + "action.yml") && !files.contains(prefix + "action.yaml")) {
            throw new IllegalStateException(
                    "Action bundle must contain action.yml or action.yaml at its root: " + actionBundlePath
            );
        }
        return files;
    }

    private GHTree readTree(RepositorySnapshot materializerSnapshot) {
        try {
            GHRepository repository = gitHub.getRepository(materializerSnapshot.repositoryName());
            GHTree tree = repository.getTreeRecursive(materializerSnapshot.commitSHA(), 1);
            if (tree == null || tree.isTruncated() || tree.getTree() == null) {
                throw new IllegalStateException("Missing or incomplete action bundle tree at "
                        + materializerSnapshot.commitSHA());
            }
            return tree;

        } catch (IOException exception) {
            String message = "Could not list action bundle files from %s at %s".formatted(
                    materializerSnapshot.repositoryName(),
                    materializerSnapshot.commitSHA()
            );
            throw new UncheckedIOException(message, exception);
        }
    }

    private String readFile(RepositorySnapshot materializerSnapshot, String path) {
        try {
            GHRepository repository = gitHub.getRepository(materializerSnapshot.repositoryName());
            GHContent file = repository.getFileContent(path, materializerSnapshot.commitSHA());
            return readContent(file);

        } catch (IOException exception) {
            String message = "Could not read action bundle file %s from %s at %s".formatted(
                    path,
                    materializerSnapshot.repositoryName(),
                    materializerSnapshot.commitSHA()
            );
            throw new UncheckedIOException(message, exception);
        }
    }

    private String readContent(GHContent file) throws IOException {
        try (InputStream content = file.read()) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String bundlePrefix(String actionBundlePath) {
        String directory = actionBundlePath.startsWith("./") ? actionBundlePath.substring(2) : actionBundlePath;
        return directory + "/";
    }
}
