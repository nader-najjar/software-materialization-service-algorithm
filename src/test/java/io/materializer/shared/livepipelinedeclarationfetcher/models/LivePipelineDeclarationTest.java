package io.materializer.shared.livepipelinedeclarationfetcher.models;

import io.materializer.shared.utilities.JsonSanitizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LivePipelineDeclarationTest {

    private static final String PIPELINE_JSON = """
            {"steps":[{"name":"build","clientIdVariable":"APP_ID","privateKeySecret":"APP_KEY","operation":"build"}],
             "repositories":[{"repositoryName":"owner/pure-java","branch":"main",
              "build_system":"universal","platforms":["aarch64-linux","x86_64-linux"]}]}
            """;

    @Test
    void readsConfiguredRepositoriesAndSteps() {
        LivePipelineDeclaration livePipelineDeclaration = JsonSanitizer.parseAndValidate(
                PIPELINE_JSON,
                LivePipelineDeclaration.class
        );
        assertEquals(
                new BuildStep("build", "build", "APP_ID", "APP_KEY"),
                livePipelineDeclaration.steps().getFirst()
        );
        DeclaredPipelineInput repository = livePipelineDeclaration.repositories().getFirst();
        assertEquals("owner/pure-java", repository.repositoryName());
        assertEquals("universal", repository.buildSystem());
    }

    @Test
    void ignoresTargetsOnBuildSteps() {
        String json = PIPELINE_JSON.replace("\"operation\":", "\"targets\":[],\"operation\":");
        LivePipelineDeclaration declaration = JsonSanitizer.parseAndValidate(json, LivePipelineDeclaration.class);
        assertEquals(new BuildStep("build", "build", "APP_ID", "APP_KEY"), declaration.steps().getFirst());
    }

    @Test
    void acceptsEmptyPipeline() {
        JsonSanitizer.parseAndValidate("{\"repositories\":[],\"steps\":[]}", LivePipelineDeclaration.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null",
        "{}",
        "[]",
        "{\"repositories\":[],\"steps\":[{\"name\":\"build\",\"operation\":\"build\"}]}",
        "{\"repositories\":[],\"steps\":[null]}",
        "{\"repositories\":[],\"steps\":[{}]}",
        "{\"steps\":[],\"repositories\":null}",
        "{\"steps\":[],\"repositories\":[null]}",
        "{\"steps\":[],\"repositories\":[{}]}"
    })
    void rejectsInvalidConfiguration(String json) {
        assertInvalid(json);
    }

    @Test
    void requiresBuildAsTheFirstStep() {
        String build = """
                {"name":"Compile","clientIdVariable":"APP_ID","privateKeySecret":"APP_KEY","operation":"build"}
                """.strip();
        String deploy = build.replace("build", "deploy").replace("Compile", "build");
        String declaration = PIPELINE_JSON.replace(build.replace("Compile", "build"), "%s");

        assertInvalid(declaration.formatted(""));
        assertInvalid(declaration.formatted(deploy));
        assertInvalid(declaration.formatted(deploy + "," + build));
        String deployment = deploymentStepJson();
        assertInvalid(declaration.formatted(deployment + "," + build));
        LivePipelineDeclaration pipeline = JsonSanitizer.parseAndValidate(
                declaration.formatted(build + "," + deployment),
                LivePipelineDeclaration.class
        );
        RunCompositeActionStep action = (RunCompositeActionStep) pipeline.steps().getLast();
        assertEquals("deploy", action.actionBundlePath());
        assertEquals(
                new ReleaseSource("owner/pure-java", "main", "aarch64-linux"),
                action.replacements().get("ALGORITHM_RELEASE")
        );
        JsonSanitizer.parseAndValidate(declaration.formatted(build), LivePipelineDeclaration.class);
    }

    private String deploymentStepJson() {
        return """
                {"name":"Deploy","operation":"run-composite-action",
                 "actionBundlePath":"deploy",
                 "replacements":{"ALGORITHM_RELEASE":{"repositoryName":"owner/pure-java",
                 "branch":"main","platform":"aarch64-linux"}}}
                """.strip();
    }

    @Test
    void rejectsUnsupportedOperationsBuildSystemsAndPlatforms() {
        assertInvalid(PIPELINE_JSON.replace("universal", "unsupported"));
        assertInvalid(PIPELINE_JSON.replace("aarch64-linux", "unsupported"));
        String extraStep = "{\"name\":\"Deploy\",\"operation\":\"deploy\"}";
        assertInvalid(PIPELINE_JSON.replace("\"operation\":\"build\"}", "\"operation\":\"build\"}," + extraStep));
    }

    @Test
    void validatesRepositoryAndPlatformValues() {
        assertInvalid(PIPELINE_JSON.replace("owner/pure-java", "invalid"));
        assertInvalid(PIPELINE_JSON.replace("aarch64-linux", ""));
    }

    @Test
    void requiresDistinctBranchesForRepeatedRepositories() {
        String entries = """
                {"steps":[
                  {"name":"build","clientIdVariable":"APP_ID","privateKeySecret":"APP_KEY","operation":"build"}],
                 "repositories":[
                  {"repositoryName":"owner/repo","branch":"main",
                   "build_system":"universal","platforms":["x86_64-linux"]},
                  {"repositoryName":"OWNER/REPO","branch":"%s",
                   "build_system":"universal","platforms":["x86_64-linux"]}
                ]}
                """.strip();

        assertInvalid(entries.formatted("main"));
        LivePipelineDeclaration livePipelineDeclaration = JsonSanitizer.parseAndValidate(
                entries.formatted("Main"),
                LivePipelineDeclaration.class
        );
        assertEquals(2, livePipelineDeclaration.repositories().size());
    }

    private void assertInvalid(String json) {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, LivePipelineDeclaration.class)
        );
    }
}
