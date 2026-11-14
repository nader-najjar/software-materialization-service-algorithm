package io.materializer.pipelineworkflowgeneration;

import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import io.materializer.pipelineworkflowgeneration.models.BuildMatrixRow;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.models.RepositorySnapshot;

public class BuildMatrixGenerator {

    @Inject
    public BuildMatrixGenerator() { }

    public List<BuildMatrixRow> generate(
            List<DeclaredPipelineInput> repositories,
            List<RepositorySnapshot> declaredRepositorySnapshots
    ) {
        List<BuildMatrixRow> rows = new ArrayList<>();
        for (DeclaredPipelineInput repository : repositories) {
            rows.addAll(generateRepositoryMatrixRows(repository, declaredRepositorySnapshots));
        }
        return rows.stream().distinct().toList();
    }

    private static List<BuildMatrixRow> generateRepositoryMatrixRows(
            DeclaredPipelineInput repository,
            List<RepositorySnapshot> declaredRepositorySnapshots
    ) {
        RepositorySnapshot snapshot = declaredRepositorySnapshots.stream()
                .filter(pin -> pin.repositoryName().equalsIgnoreCase(repository.repositoryName()))
                .filter(pin -> pin.branch().equals(repository.branch()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing source pin for %s@%s".formatted(repository.repositoryName(), repository.branch())
                ));
        List<BuildMatrixRow> rows = new ArrayList<>();
        for (String platform : repository.platforms()) {
            rows.add(new BuildMatrixRow(
                    repository.repositoryName(),
                    snapshot.commitSHA(),
                    repository.buildSystem(),
                    platform,
                    runnerForPlatform(platform)
            ));
        }
        return rows;
    }

    private static String runnerForPlatform(String platform) {
        return switch (platform) {
            case "aarch64-linux" -> "ubuntu-24.04-arm";
            case "x86_64-linux" -> "ubuntu-24.04";
            default -> throw new IllegalArgumentException("Unsupported build platform: " + platform);
        };
    }
}
