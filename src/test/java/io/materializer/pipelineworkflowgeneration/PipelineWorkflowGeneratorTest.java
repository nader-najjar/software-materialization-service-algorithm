package io.materializer.pipelineworkflowgeneration;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.materializer.shared.livepipelinedeclarationfetcher.models.BuildStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelinedeclarationfetcher.models.PackageOCIImageToGHCRStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.ReleaseSource;
import io.materializer.shared.livepipelinedeclarationfetcher.models.RunCompositeActionStep;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.ResourceUtility;
import io.materializer.shared.utilities.YamlMapperUtility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PipelineWorkflowGeneratorTest {

    private final ActionBundleReader actionBundleReader = mock(ActionBundleReader.class);
    private final PipelineWorkflowGenerator generator = new PipelineWorkflowGenerator(
            new BuildMatrixGenerator(),
            new PackagingMatrixGenerator(),
            actionBundleReader
    );

    @Test
    void generatesTheExpectedWorkflowYaml() throws IOException {
        RepositorySnapshot materializer = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 1L);
        List<String> platforms = List.of("aarch64-linux", "x86_64-linux");
        List<DeclaredPipelineInput> repositories = List.of(
                new DeclaredPipelineInput("nader-najjar/pure-java", "main", "universal", platforms),
                new DeclaredPipelineInput("nader-najjar/pure-javascript", "main", "universal", platforms)
        );
        String javaSHA = "28e79fd674af4ba6387765247272fed25578430d";
        String javascriptSHA = "e90d63d9b81312d83738bb1654442d54ad40e36e";
        List<RepositorySnapshot> sources = List.of(
                new RepositorySnapshot("nader-najjar/pure-java", "main", javaSHA, 1L),
                new RepositorySnapshot("nader-najjar/pure-javascript", "main", javascriptSHA, 1L)
        );
        BuildStep step = new BuildStep("Build", "build", "APP_ID", "APP_KEY");
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(repositories, List.of(step));
        Map<String, String> files = generator.generate(
                declaration,
                new LivePipelineInputs(materializer, sources)
        );
        verifyNoInteractions(actionBundleReader);
        String expected = ResourceUtility.readString("/pipelineworkflowgeneration/materialize.yml");
        String yaml = files.get(".github/workflows/materialize.yml");
        assertEquals(YamlMapperUtility.MAPPER.readTree(expected), YamlMapperUtility.MAPPER.readTree(yaml));
    }

    @ParameterizedTest
    @ValueSource(strings = {"action.yml", "action.yaml"})
    void chainsJobsAndReplacesActionValuesFromSavedBuilds(String actionFile) throws IOException {
        RepositorySnapshot materializer = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 1L);
        List<RepositorySnapshot> pins = List.of(
                new RepositorySnapshot("owner/app", "main", "b".repeat(40), 1L),
                new RepositorySnapshot("owner/app", "release", "c".repeat(40), 2L)
        );
        String actionText = "release: @@APP@@\nagain: @@APP@@\nmain: MAIN\nother: @@UNKNOWN@@\n";
        when(actionBundleReader.readFiles(materializer, "deploy"))
                .thenReturn(Map.of(actionFile, actionText, "scripts/deploy.py", actionText));
        when(actionBundleReader.readFiles(materializer, "check"))
                .thenReturn(Map.of("action.yml", "check action"));
        Map<String, String> files = generator.generate(
                chainedDeclaration(),
                new LivePipelineInputs(materializer, pins)
        );
        assertReplacedActionFiles(files, actionFile, actionText);
        verify(actionBundleReader).readFiles(materializer, "deploy");
        verify(actionBundleReader).readFiles(materializer, "check");
        verifyNoMoreInteractions(actionBundleReader);
        assertChainedJobs(files.get(".github/workflows/materialize.yml"));
    }

    private LivePipelineDeclaration chainedDeclaration() {
        BuildStep build = new BuildStep("Compile", "build", "APP_ID", "APP_KEY");
        RunCompositeActionStep withoutInputs = new RunCompositeActionStep(
                "Check",
                "run-composite-action",
                "check",
                null,
                Map.of()
        );
        return new LivePipelineDeclaration(
                List.of(repository("main"), repository("release")),
                List.of(build, deploymentWithReplacements(), withoutInputs)
        );
    }

    private RunCompositeActionStep deploymentWithReplacements() {
        return new RunCompositeActionStep(
                "Deploy workflows",
                "run-composite-action",
                "deploy",
                Map.of("private-key", "${{ secrets.APP_KEY }}"),
                Map.of(
                        "@@APP@@",
                        new ReleaseSource("OWNER/APP", "release", "aarch64-linux"),
                        "MAIN",
                        new ReleaseSource("owner/app", "main", "aarch64-linux")
                )
        );
    }

    private void assertReplacedActionFiles(Map<String, String> files, String actionFile, String actionText) {
        String releaseName = "owner--app--aarch64-linux--" + "c".repeat(40);
        String mainName = "owner--app--aarch64-linux--" + "b".repeat(40);
        assertEquals(
                "release: " + releaseName + "\nagain: " + releaseName
                        + "\nmain: " + mainName + "\nother: @@UNKNOWN@@\n",
                files.get(".github/actions/step_2/" + actionFile)
        );
        assertEquals(actionText, files.get(".github/actions/step_2/scripts/deploy.py"));
        assertEquals("check action", files.get(".github/actions/step_3/action.yml"));
    }

    private void assertChainedJobs(String yaml) throws IOException {
        JsonNode jobs = YamlMapperUtility.MAPPER.readTree(yaml).required("jobs");
        assertEquals("${{ secrets.APP_KEY }}", jobs.at("/step_2/steps/1/with/private-key").asText());
        assertTrue(jobs.at("/step_3/steps/1/with").isMissingNode());
        assertEquals("$/.github/actions/step_2", jobs.at("/step_2/steps/1/uses").asText());
        assertEquals("$/.github/actions/step_3", jobs.at("/step_3/steps/1/uses").asText());
        assertEquals("Deploy workflows", jobs.required("step_2").required("name").asText());
        assertEquals("step_1", jobs.required("step_2").required("needs").asText());
        assertEquals("step_2", jobs.required("step_3").required("needs").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"aarch64-linux", "x86_64-linux"})
    void generatesPackagingJobForSelectedSavedRelease(String platform) throws IOException {
        RepositorySnapshot materializer = new RepositorySnapshot("owner/mat", "main", "a".repeat(40), 1L);
        List<RepositorySnapshot> savedSourceSnapshots = List.of(
                new RepositorySnapshot("owner/app", "main", "b".repeat(40), 1L),
                new RepositorySnapshot("owner/app", "release", "c".repeat(40), 2L)
        );
        when(actionBundleReader.readFiles(materializer, "check")).thenReturn(Map.of("action.yml", "check action"));
        Map<String, String> files = generator.generate(
                packagingDeclaration(platform),
                new LivePipelineInputs(materializer, savedSourceSnapshots)
        );
        String workflowYaml = files.get(".github/workflows/materialize.yml");
        JsonNode jobs = YamlMapperUtility.MAPPER.readTree(workflowYaml).required("jobs");
        assertPackagingJobOrder(jobs);
        assertPackagingCredentials(jobs.required("step_2"));
        assertPackagingMatrix(jobs.at("/step_2/strategy/matrix/include"), platform);
    }

    private LivePipelineDeclaration packagingDeclaration(String platform) {
        BuildStep build = new BuildStep("Build", "build", "APP_ID", "APP_KEY");
        PackageOCIImageToGHCRStep packaging = new PackageOCIImageToGHCRStep(
                "Package Selected Image",
                "package-oci-image-to-ghcr",
                "PACKAGE_APP_ID",
                "PACKAGE_APP_KEY",
                List.of(new ReleaseSource("OWNER/APP", "release", platform))
        );
        RunCompositeActionStep deploy = new RunCompositeActionStep(
                "Deploy",
                "run-composite-action",
                "check",
                Map.of(),
                Map.of()
        );
        return new LivePipelineDeclaration(
                packagingBuildInputs(),
                List.of(build, packaging, deploy)
        );
    }

    private List<DeclaredPipelineInput> packagingBuildInputs() {
        List<String> platforms = List.of("aarch64-linux", "x86_64-linux");
        return List.of(
                new DeclaredPipelineInput("owner/app", "main", "universal", platforms),
                new DeclaredPipelineInput("owner/app", "release", "universal", platforms)
        );
    }

    private void assertPackagingJobOrder(JsonNode jobs) {
        JsonNode packaging = jobs.required("step_2");
        assertEquals(
                "Package Selected Image ${{ matrix.repositoryName }} (${{ matrix.platform }})",
                packaging.required("name").asText()
        );
        assertEquals("${{ matrix.runner }}", packaging.required("runs-on").asText());
        assertEquals("step_1", packaging.required("needs").asText());
        assertEquals("step_2", jobs.required("step_3").required("needs").asText());
    }

    private void assertPackagingCredentials(JsonNode packaging) {
        assertEquals("PACKAGE_APP_ID", packaging.at("/env/CLIENT_ID_VARIABLE").asText());
        assertEquals("PACKAGE_APP_KEY", packaging.at("/env/PRIVATE_KEY_SECRET").asText());
        JsonNode token = packaging.required("steps").required(3);
        assertEquals("create-repository-token", token.required("id").asText());
        assertEquals("${{ vars[env.CLIENT_ID_VARIABLE] }}", token.at("/with/client-id").asText());
        assertEquals("${{ secrets[env.PRIVATE_KEY_SECRET] }}", token.at("/with/private-key").asText());
        assertEquals("${{ env.REPOSITORY_OWNER }}", token.at("/with/owner").asText());
        assertEquals("${{ matrix.repositoryName }}", token.at("/with/repositories").asText());
        assertEquals("read", token.at("/with/permission-contents").asText());
        assertEquals(
                "${{ steps.create-repository-token.outputs.token }}",
                packaging.at("/steps/4/env/GH_TOKEN").asText()
        );
    }

    private void assertPackagingMatrix(JsonNode rows, String platform) {
        assertEquals(1, rows.size());
        JsonNode row = rows.required(0);
        assertEquals("owner/app", row.required("repositoryName").asText());
        assertEquals("c".repeat(40), row.required("commitSHA").asText());
        assertEquals(platform, row.required("platform").asText());
        assertEquals(
                platform.equals("aarch64-linux") ? "ubuntu-24.04-arm" : "ubuntu-24.04",
                row.required("runner").asText()
        );
        assertFalse(row.has("buildSystem"));
    }

    @Test
    void copiesRepeatedBundlesIntoSeparateStepDirectories() {
        RepositorySnapshot materializer = new RepositorySnapshot("owner/mat", "main", "d".repeat(40), 1L);
        Map<String, String> bundle = deploymentBundle();
        when(actionBundleReader.readFiles(materializer, "./deploy")).thenReturn(bundle);
        RepositorySnapshot source = new RepositorySnapshot("owner/app", "main", "e".repeat(40), 1L);
        Map<String, String> files = generator.generate(
                repeatedBundleDeclaration(),
                new LivePipelineInputs(materializer, List.of(source))
        );
        assertBundleFiles(files, bundle);
        verify(actionBundleReader, times(2)).readFiles(materializer, "./deploy");
        verifyNoMoreInteractions(actionBundleReader);
    }

    private Map<String, String> deploymentBundle() {
        return Map.of(
                "action.yaml",
                "name: Deploy\nruns:\n  using: composite\n",
                "scripts/deploy/run.sh",
                "#!/bin/sh\necho deploy\n",
                "config/settings.json",
                "{\"enabled\":true}\n"
        );
    }

    private LivePipelineDeclaration repeatedBundleDeclaration() {
        RunCompositeActionStep deploy = new RunCompositeActionStep(
                "Deploy",
                "run-composite-action",
                "./deploy",
                Map.of(),
                Map.of()
        );
        BuildStep build = new BuildStep("Build", "build", "APP_ID", "APP_KEY");
        return new LivePipelineDeclaration(
                List.of(repository("main")),
                List.of(build, deploy, deploy)
        );
    }

    private void assertBundleFiles(Map<String, String> files, Map<String, String> bundle) {
        for (int step = 2; step <= 3; step++) {
            String directory = ".github/actions/step_" + step;
            assertEquals(bundle.get("action.yaml"), files.get(directory + "/action.yaml"));
            assertEquals(bundle.get("scripts/deploy/run.sh"), files.get(directory + "/scripts/deploy/run.sh"));
            assertEquals(bundle.get("config/settings.json"), files.get(directory + "/config/settings.json"));
        }
        assertFalse(files.keySet().stream().anyMatch(path -> path.startsWith("deploy/")));
    }

    private DeclaredPipelineInput repository(String branch) {
        return new DeclaredPipelineInput("owner/app", branch, "universal", List.of("aarch64-linux"));
    }
}
