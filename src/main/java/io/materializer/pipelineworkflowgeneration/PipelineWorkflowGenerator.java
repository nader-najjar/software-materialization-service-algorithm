package io.materializer.pipelineworkflowgeneration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import io.materializer.pipelineworkflowgeneration.models.BuildMatrixRow;
import io.materializer.pipelineworkflowgeneration.models.PackagingMatrixRow;
import io.materializer.shared.livepipelinedeclarationfetcher.models.ActionReplacement;
import io.materializer.shared.livepipelinedeclarationfetcher.models.BuildStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelinedeclarationfetcher.models.PackagingStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.RunCompositeActionStep;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.ResourceUtility;
import io.materializer.shared.utilities.YamlMapperUtility;

public class PipelineWorkflowGenerator {

    private final BuildMatrixGenerator buildMatrixGenerator;
    private final PackagingMatrixGenerator packagingMatrixGenerator;
    private final ActionBundleReader actionBundleReader;

    @Inject
    public PipelineWorkflowGenerator(
            BuildMatrixGenerator buildMatrixGenerator,
            PackagingMatrixGenerator packagingMatrixGenerator,
            ActionBundleReader actionBundleReader
    ) {
        this.buildMatrixGenerator = buildMatrixGenerator;
        this.packagingMatrixGenerator = packagingMatrixGenerator;
        this.actionBundleReader = actionBundleReader;
    }

    public Map<String, String> generate(
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs
    ) {
        String template = ResourceUtility.readString("/pipelineworkflowgeneration/materialize-template.yml");
        try {
            ObjectNode workflow = (ObjectNode) YamlMapperUtility.MAPPER.readTree(template);
            Map<String, String> files = new LinkedHashMap<>();
            addActionResources(files);
            addJobs((ObjectNode) workflow.required("jobs"), declaration, inputs, files);
            String yaml = YamlMapperUtility.MAPPER.writeValueAsString(workflow);
            files.put(".github/workflows/materialize.yml", yaml);
            return files;

        } catch (IOException exception) {
            throw new UncheckedIOException("Could not generate materialization workflow", exception);
        }
    }

    private void addActionResources(Map<String, String> files) {
        List<String> resourcePaths = List.of(
                "actions/reject-manual-retries/action.yml",
                "actions/reject-manual-retries/check.sh",
                "actions/universal-build/action.yml",
                "actions/find-release-artifact/action.yml",
                "actions/find-release-artifact/find.cjs",
                "actions/publish-release-artifact/action.yml",
                "actions/publish-release-artifact/publish.cjs"
        );

        for (String resourcePath : resourcePaths) {
            String content = ResourceUtility.readString("/" + resourcePath);
            files.put(".github/" + resourcePath, content);
        }
    }

    private void addJobs(
            ObjectNode jobs,
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs,
            Map<String, String> files
    ) throws JsonProcessingException {
        String previousJobId = null;
        for (DeclaredPipelineStep step : declaration.steps()) {
            String jobId = "step_" + (jobs.size() + 1);
            ObjectNode job = generateJob(step, jobId, declaration, inputs, files);
            if (previousJobId != null) { job.put("needs", previousJobId); }
            jobs.set(jobId, job);
            previousJobId = jobId;
        }
    }

    private ObjectNode generateJob(
            DeclaredPipelineStep step,
            String jobId,
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs,
            Map<String, String> files
    ) throws JsonProcessingException {
        return switch (step.operation()) {
            case "build" -> generateBuildJob((BuildStep) step, declaration, inputs);
            case "package-oci-image-to-ghcr" -> generatePackagingJob(
                    (PackagingStep) step,
                    declaration,
                    inputs
            );
            case "run-composite-action" -> generateActionJob(
                    (RunCompositeActionStep) step,
                    declaration,
                    inputs,
                    ".github/actions/" + jobId,
                    files
            );
            default -> throw new IllegalArgumentException("Unknown operation: " + step.operation());
        };
    }

    private ObjectNode generateBuildJob(
            BuildStep buildStep,
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs
    ) throws JsonProcessingException {
        String template = ResourceUtility.readString("/pipelineworkflowgeneration/build-job-template.yml");
        ObjectNode build = (ObjectNode) YamlMapperUtility.MAPPER.readTree(template);
        build.put(
                "name",
                buildStep.name() + " ${{ matrix.repositoryName }} (${{ matrix.platform }})"
        );
        ObjectNode buildEnvironment = (ObjectNode) build.required("env");
        buildEnvironment.put("CLIENT_ID_VARIABLE", buildStep.clientIdVariable());
        buildEnvironment.put("PRIVATE_KEY_SECRET", buildStep.privateKeySecret());
        ObjectNode matrix = (ObjectNode) build.required("strategy").required("matrix");
        List<BuildMatrixRow> builds = buildMatrixGenerator.generate(
                declaration.repositories(),
                inputs.declaredRepositorySnapshots()
        );
        matrix.set("include", YamlMapperUtility.MAPPER.valueToTree(builds));
        return build;
    }

    private ObjectNode generatePackagingJob(
            PackagingStep packagingStep,
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs
    ) throws JsonProcessingException {
        String template = ResourceUtility.readString("/pipelineworkflowgeneration/packaging-job-template.yml");
        ObjectNode packaging = (ObjectNode) YamlMapperUtility.MAPPER.readTree(template);
        packaging.put(
                "name",
                packagingStep.name() + " ${{ matrix.repositoryName }} (${{ matrix.platform }})"
        );
        ObjectNode packagingEnvironment = (ObjectNode) packaging.required("env");
        packagingEnvironment.put("CLIENT_ID_VARIABLE", packagingStep.clientIdVariable());
        packagingEnvironment.put("PRIVATE_KEY_SECRET", packagingStep.privateKeySecret());
        ObjectNode matrix = (ObjectNode) packaging.required("strategy").required("matrix");
        List<PackagingMatrixRow> packagingRows = packagingMatrixGenerator.generate(
                packagingStep.targets(),
                declaration.repositories(),
                inputs.declaredRepositorySnapshots()
        );
        matrix.set("include", YamlMapperUtility.MAPPER.valueToTree(packagingRows));
        return packaging;
    }

    private ObjectNode generateActionJob(
            RunCompositeActionStep action,
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs,
            String directory,
            Map<String, String> files
    ) throws JsonProcessingException {
        addActionBundleFiles(action, declaration, inputs, directory, files);
        String template = ResourceUtility.readString(
                "/pipelineworkflowgeneration/run-composite-action-job-template.yml"
        );
        ObjectNode job = (ObjectNode) YamlMapperUtility.MAPPER.readTree(template.replace("@@ACTION_PATH@@", directory));
        job.put("name", action.name());
        if (action.with() != null && !action.with().isEmpty()) {
            ObjectNode actionStep = (ObjectNode) job.required("steps").required(1);
            actionStep.set("with", YamlMapperUtility.MAPPER.valueToTree(action.with()));
        }
        return job;
    }

    private void addActionBundleFiles(
            RunCompositeActionStep action,
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs,
            String directory,
            Map<String, String> files
    ) {
        Map<String, String> bundle = actionBundleReader.readFiles(
                inputs.materializerRepositorySnapshot(),
                action.actionBundlePath()
        );
        Map<String, String> replacements = resolveActionReplacements(action, declaration, inputs);
        for (Map.Entry<String, String> file : bundle.entrySet()) {
            String content = replaceActionValues(file.getKey(), file.getValue(), replacements);
            files.put(directory + "/" + file.getKey(), content);
        }
    }

    private String replaceActionValues(
            String path,
            String content,
            Map<String, String> replacements
    ) {
        if (!path.equals("action.yml") && !path.equals("action.yaml")) { return content; }
        String result = content;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            result = result.replace(replacement.getKey(), replacement.getValue());
        }
        return result;
    }

    private Map<String, String> resolveActionReplacements(
            RunCompositeActionStep action,
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs
    ) {
        Map<String, String> replacements = new LinkedHashMap<>();
        for (Map.Entry<String, ActionReplacement> replacement : action.replacements().entrySet()) {
            String value = resolveActionReplacement(replacement.getValue(), declaration, inputs);
            replacements.put(replacement.getKey(), value);
        }
        return replacements;
    }

    private String resolveActionReplacement(
            ActionReplacement source,
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs
    ) {
        String releaseName = resolveReleaseName(source, declaration, inputs);
        return switch (source.artifactKind()) {
            case "release-artifact" -> releaseName;
            case "oci-image" -> resolveImageReference(source, declaration, releaseName);
            default -> throw new IllegalArgumentException(
                    "Unsupported replacement artifact kind: " + source.artifactKind()
            );
        };
    }

    private String resolveImageReference(
            ActionReplacement source,
            LivePipelineDeclaration declaration,
            String releaseName
    ) {
        boolean packaged = declaration.steps().size() > 1
                && declaration.steps().get(1) instanceof PackagingStep packaging
                && packaging.targets().stream().anyMatch(target ->
                        target.repositoryName().equalsIgnoreCase(source.repositoryName())
                                && target.branch().equals(source.branch())
                                && target.platform().equals(source.platform()));

        if (!packaged) {
            throw new IllegalArgumentException("No declared OCI image for %s@%s on %s".formatted(
                    source.repositoryName(),
                    source.branch(),
                    source.platform()
            ));
        }
        return "ghcr.io/" + source.repositoryName().toLowerCase(Locale.ROOT) + ":" + releaseName;
    }

    private String resolveReleaseName(
            ActionReplacement source,
            LivePipelineDeclaration declaration,
            LivePipelineInputs inputs
    ) {
        DeclaredPipelineInput repository = declaration.repositories().stream()
                .filter(input -> input.repositoryName().equalsIgnoreCase(source.repositoryName()))
                .filter(input -> input.branch().equals(source.branch()))
                .filter(input -> input.platforms().contains(source.platform()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No declared build for %s@%s on %s".formatted(
                                source.repositoryName(),
                                source.branch(),
                                source.platform()
                        )
                ));
        RepositorySnapshot snapshot = inputs.declaredRepositorySnapshots().stream()
                .filter(pin -> pin.repositoryName().equalsIgnoreCase(repository.repositoryName()))
                .filter(pin -> pin.branch().equals(repository.branch()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing source pin for %s@%s".formatted(repository.repositoryName(), repository.branch())
                ));
        return repository.repositoryName().replace("/", "--") + "--" + source.platform() + "--" + snapshot.commitSHA();
    }
}
