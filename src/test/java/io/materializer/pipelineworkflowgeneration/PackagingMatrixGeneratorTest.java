package io.materializer.pipelineworkflowgeneration;

import java.util.List;

import io.materializer.pipelineworkflowgeneration.models.PackagingMatrixRow;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.ReleaseSource;
import io.materializer.shared.models.RepositorySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackagingMatrixGeneratorTest {

    private final PackagingMatrixGenerator generator = new PackagingMatrixGenerator();

    @Test
    void selectsOnlyRequestedBuildsAndTheirBranchPins() {
        String mainSHA = "a".repeat(40);
        List<ReleaseSource> targets = List.of(
                new ReleaseSource("OWNER/SOURCE", "release", "x86_64-linux"),
                new ReleaseSource("owner/source", "main", "aarch64-linux"),
                new ReleaseSource("OWNER/SOURCE", "main", "aarch64-linux")
        );
        List<RepositorySnapshot> snapshots = List.of(
                new RepositorySnapshot("owner/source", "main", mainSHA, 1L),
                new RepositorySnapshot("OWNER/source", "release", "b".repeat(40), 2L)
        );
        List<PackagingMatrixRow> rows = generator.generate(
                targets,
                packagingRepositories(),
                snapshots
        );
        assertEquals(
                List.of(
                        new PackagingMatrixRow("owner/source", "b".repeat(40), "x86_64-linux", "ubuntu-24.04"),
                        new PackagingMatrixRow("owner/source", mainSHA, "aarch64-linux", "ubuntu-24.04-arm")
                ),
                rows
        );
    }

    @ParameterizedTest
    @CsvSource({
        "owner/missing, main, aarch64-linux",
        "owner/source, missing, aarch64-linux",
        "owner/source, Main, aarch64-linux",
        "owner/source, release, aarch64-linux"
    })
    void rejectsTargetsWithoutAConfiguredBuild(String repositoryName, String branch, String platform) {
        ReleaseSource target = new ReleaseSource(repositoryName, branch, platform);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(
                        List.of(target),
                        packagingRepositories(),
                        List.of()
                )
        );
        assertEquals(
                "No declared build for %s@%s on %s".formatted(repositoryName, branch, platform),
                exception.getMessage()
        );
    }

    @Test
    void rejectsPackagingTargetWhenOnlyAnotherBranchHasAPin() {
        ReleaseSource target = new ReleaseSource("owner/source", "release", "x86_64-linux");
        RepositorySnapshot main = new RepositorySnapshot("owner/source", "main", "a".repeat(40), 1L);
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> generator.generate(
                        List.of(target),
                        packagingRepositories(),
                        List.of(main)
                )
        );
        assertEquals("Missing source pin for owner/source@release", exception.getMessage());
    }

    private List<DeclaredPipelineInput> packagingRepositories() {
        return List.of(
                new DeclaredPipelineInput(
                        "owner/source",
                        "main",
                        "universal",
                        List.of("aarch64-linux", "x86_64-linux")
                ),
                new DeclaredPipelineInput("owner/source", "release", "universal", List.of("x86_64-linux")),
                new DeclaredPipelineInput("owner/other", "main", "universal", List.of("aarch64-linux"))
        );
    }
}
