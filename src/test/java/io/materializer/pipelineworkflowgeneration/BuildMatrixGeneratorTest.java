package io.materializer.pipelineworkflowgeneration;

import java.util.List;

import io.materializer.pipelineworkflowgeneration.models.BuildMatrixRow;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.models.RepositorySnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildMatrixGeneratorTest {

    @Test
    void buildsEachPlatformWithThePinForTheDeclaredBranch() {
        DeclaredPipelineInput repository = new DeclaredPipelineInput(
                "owner/source",
                "release",
                "universal",
                List.of("aarch64-linux", "x86_64-linux")
        );
        RepositorySnapshot main = new RepositorySnapshot("owner/source", "main", "a".repeat(40));
        RepositorySnapshot release = new RepositorySnapshot("OWNER/source", "release", "b".repeat(40));

        List<BuildMatrixRow> rows = new BuildMatrixGenerator().generate(
                List.of(repository),
                List.of(main, release)
        );

        String releaseSHA = release.commitSHA();
        assertEquals(List.of(
                new BuildMatrixRow("owner/source", releaseSHA, "universal", "aarch64-linux", "ubuntu-24.04-arm"),
                new BuildMatrixRow("owner/source", releaseSHA, "universal", "x86_64-linux", "ubuntu-24.04")
        ), rows);
    }

    @Test
    void buildsIdenticalPinsOnlyOnceAcrossBranchesAndDuplicatePlatforms() {
        List<String> platforms = List.of("x86_64-linux", "x86_64-linux", "aarch64-linux");
        List<DeclaredPipelineInput> inputs = List.of(
                new DeclaredPipelineInput("owner/source", "main", "universal", platforms),
                new DeclaredPipelineInput("owner/source", "release", "universal", platforms)
        );
        String commitSHA = "a".repeat(40);
        List<RepositorySnapshot> snapshots = List.of(
                new RepositorySnapshot("owner/source", "main", commitSHA),
                new RepositorySnapshot("owner/source", "release", commitSHA)
        );

        assertEquals(List.of(
                new BuildMatrixRow("owner/source", commitSHA, "universal", "x86_64-linux", "ubuntu-24.04"),
                new BuildMatrixRow("owner/source", commitSHA, "universal", "aarch64-linux", "ubuntu-24.04-arm")
        ), new BuildMatrixGenerator().generate(inputs, snapshots));
    }

    @Test
    void rejectsMissingPinsAndUnsupportedPlatforms() {
        DeclaredPipelineInput input = new DeclaredPipelineInput("owner/source", "main", "universal", List.of("other"));
        RepositorySnapshot snapshot = new RepositorySnapshot("owner/source", "main", "a".repeat(40));
        assertThrows(
                IllegalStateException.class,
                () -> new BuildMatrixGenerator().generate(List.of(input), List.of())
        );
        assertThrows(IllegalArgumentException.class,
                () -> new BuildMatrixGenerator().generate(List.of(input), List.of(snapshot)));
    }
}
