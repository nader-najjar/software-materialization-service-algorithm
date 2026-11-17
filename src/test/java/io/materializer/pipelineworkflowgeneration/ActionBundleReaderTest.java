package io.materializer.pipelineworkflowgeneration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.materializer.shared.models.RepositorySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionBundleReaderTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final ActionBundleReader reader = new ActionBundleReader(gitHub);
    private final RepositorySnapshot materializerSnapshot = new RepositorySnapshot(
            "owner/materializer",
            "main",
            "a".repeat(40),
            1L
    );

    @ParameterizedTest
    @ValueSource(strings = {"./deploy", "deploy"})
    void readsNestedTextFilesFromSavedCommitAndClosesStreams(String actionBundlePath) throws IOException {
        GHTree tree = mockTree();
        List<GHTreeEntry> entries = List.of(
                entry("deploy/action.yml", "blob"),
                entry("deploy/scripts/deploy/run.sh", "blob"),
                entry("deployment/action.yml", "blob")
        );
        when(tree.getTree()).thenReturn(entries);
        InputStream action = mockFile("deploy/action.yml", "name: Déploy\n");
        InputStream script = mockFile("deploy/scripts/deploy/run.sh", "echo deploy\n");

        Map<String, String> expected = Map.of(
                "action.yml",
                "name: Déploy\n",
                "scripts/deploy/run.sh",
                "echo deploy\n"
        );
        assertEquals(expected, reader.readFiles(materializerSnapshot, actionBundlePath));
        verify(repository).getTreeRecursive(materializerSnapshot.commitSHA(), 1);
        verify(action).close();
        verify(script).close();
    }

    @Test
    void preservesContentReadFailureAndClosesStream() throws IOException {
        GHTree tree = mockTree();
        List<GHTreeEntry> entries = List.of(entry("deploy/action.yml", "blob"));
        when(tree.getTree()).thenReturn(entries);
        GHContent file = mock(GHContent.class);
        InputStream content = mock(InputStream.class);
        IOException failure = new IOException("Content unavailable");
        when(repository.getFileContent("deploy/action.yml", materializerSnapshot.commitSHA())).thenReturn(file);
        when(file.read()).thenReturn(content);
        when(content.readAllBytes()).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> reader.readFiles(materializerSnapshot, "deploy")
        );

        assertSame(failure, exception.getCause());
        assertTrue(exception.getMessage().contains("deploy/action.yml"));
        assertTrue(exception.getMessage().contains(materializerSnapshot.commitSHA()));
        verify(content).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"action.yml", "action.yaml"})
    void listsFullNestedFilePathsFromSavedCommit(String actionFile) throws IOException {
        GHTree tree = mockTree();
        List<GHTreeEntry> entries = List.of(
                entry("deploy", "tree"),
                entry("deploy/" + actionFile, "blob"),
                entry("deploy/scripts", "tree"),
                entry("deploy/scripts/run.sh", "blob"),
                entry("deployment/action.yml", "blob"),
                entry("other/run.sh", "blob")
        );
        when(tree.getTree()).thenReturn(entries);

        List<String> expected = List.of("deploy/" + actionFile, "deploy/scripts/run.sh");
        assertEquals(expected, reader.listFiles(materializerSnapshot, "./deploy"));
        assertEquals(expected, reader.listFiles(materializerSnapshot, "deploy"));
        verify(repository, times(2)).getTreeRecursive(materializerSnapshot.commitSHA(), 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deploy/scripts/run.sh", "deploy/nested/action.yml", "deploy/nested/action.yaml"})
    void rejectsBundleWithoutRootActionYaml(String filePath) throws IOException {
        GHTree tree = mockTree();
        List<GHTreeEntry> entries = List.of(entry(filePath, "blob"));
        when(tree.getTree()).thenReturn(entries);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reader.listFiles(materializerSnapshot, "./deploy")
        );

        assertEquals(
                "Action bundle must contain action.yml or action.yaml at its root: ./deploy",
                exception.getMessage()
        );
    }

    @Test
    void rejectsTruncatedTreeEvenWhenBundleFilesArePresent() throws IOException {
        GHTree tree = mockTree();
        List<GHTreeEntry> entries = List.of(entry("deploy/action.yml", "blob"));
        when(tree.getTree()).thenReturn(entries);
        when(tree.isTruncated()).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> reader.listFiles(materializerSnapshot, "./deploy"));
    }

    @Test
    void rejectsMissingTree() throws IOException {
        when(gitHub.getRepository(materializerSnapshot.repositoryName())).thenReturn(repository);

        assertThrows(IllegalStateException.class, () -> reader.listFiles(materializerSnapshot, "./deploy"));
    }

    @Test
    void rejectsMissingBundle() throws IOException {
        GHTree tree = mockTree();
        List<GHTreeEntry> entries = List.of(entry("deployment/action.yml", "blob"));
        when(tree.getTree()).thenReturn(entries);

        assertThrows(IllegalStateException.class, () -> reader.listFiles(materializerSnapshot, "./deploy"));
    }

    @Test
    void preservesGitHubFailure() throws IOException {
        IOException failure = new IOException("Repository unavailable");
        when(gitHub.getRepository(materializerSnapshot.repositoryName())).thenThrow(failure);

        UncheckedIOException exception = assertThrows(
                UncheckedIOException.class,
                () -> reader.listFiles(materializerSnapshot, "./deploy")
        );

        assertSame(failure, exception.getCause());
    }

    private InputStream mockFile(String path, String text) throws IOException {
        GHContent file = mock(GHContent.class);
        InputStream content = spy(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        when(repository.getFileContent(path, materializerSnapshot.commitSHA())).thenReturn(file);
        when(file.read()).thenReturn(content);
        return content;
    }

    private GHTree mockTree() throws IOException {
        GHTree tree = mock(GHTree.class);
        when(gitHub.getRepository(materializerSnapshot.repositoryName())).thenReturn(repository);
        when(repository.getTreeRecursive(materializerSnapshot.commitSHA(), 1)).thenReturn(tree);
        return tree;
    }

    private GHTreeEntry entry(String path, String type) {
        GHTreeEntry entry = mock(GHTreeEntry.class);
        when(entry.getPath()).thenReturn(path);
        when(entry.getType()).thenReturn(type);
        return entry;
    }
}
