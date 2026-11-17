package io.materializer.pipelineworkflowgeneration;

import java.util.List;

import com.google.inject.Inject;
import io.materializer.pipelineworkflowgeneration.models.PackagingMatrixRow;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.ReleaseSource;
import io.materializer.shared.models.RepositorySnapshot;

public class PackagingMatrixGenerator {

    @Inject
    public PackagingMatrixGenerator() { }

    public List<PackagingMatrixRow> generate(
            List<ReleaseSource> targets,
            List<DeclaredPipelineInput> configuredBuildInputs,
            List<RepositorySnapshot> savedSourceSnapshots
    ) {
        return targets.stream()
                .map(target -> generateRow(target, configuredBuildInputs, savedSourceSnapshots))
                .distinct()
                .toList();
    }

    private static PackagingMatrixRow generateRow(
            ReleaseSource target,
            List<DeclaredPipelineInput> configuredBuildInputs,
            List<RepositorySnapshot> savedSourceSnapshots
    ) {
        DeclaredPipelineInput repository = findRepository(target, configuredBuildInputs);
        RepositorySnapshot snapshot = savedSourceSnapshots.stream()
                .filter(pin -> pin.repositoryName().equalsIgnoreCase(repository.repositoryName()))
                .filter(pin -> pin.branch().equals(repository.branch()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing source pin for %s@%s".formatted(repository.repositoryName(), repository.branch())
                ));
        return new PackagingMatrixRow(
                repository.repositoryName(),
                snapshot.commitSHA(),
                target.platform(),
                runnerForPlatform(target.platform())
        );
    }

    private static DeclaredPipelineInput findRepository(
            ReleaseSource target,
            List<DeclaredPipelineInput> configuredBuildInputs
    ) {
        return configuredBuildInputs.stream()
                .filter(input -> input.repositoryName().equalsIgnoreCase(target.repositoryName()))
                .filter(input -> input.branch().equals(target.branch()))
                .filter(input -> input.platforms().contains(target.platform()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No declared build for %s@%s on %s".formatted(
                                target.repositoryName(),
                                target.branch(),
                                target.platform()
                        )
                ));
    }

    private static String runnerForPlatform(String platform) {
        return switch (platform) {
            case "aarch64-linux" -> "ubuntu-24.04-arm";
            case "x86_64-linux" -> "ubuntu-24.04";
            default -> throw new IllegalArgumentException("Unsupported build platform: " + platform);
        };
    }
}
