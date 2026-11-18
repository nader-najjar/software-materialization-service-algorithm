package io.materializer.shared.livepipelinedeclarationfetcher.models;

import java.util.List;

import io.materializer.shared.utilities.JsonSanitizer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackagingStepTest {

    @ParameterizedTest
    @ValueSource(strings = {"aarch64-linux", "x86_64-linux"})
    void readsPackagingStepAfterBuild(String platform) {
        String target = """
                [{"repositoryName":"owner/pure-java","branch":"main","platform":"%s"}]
                """.replace("%s", platform);
        LivePipelineDeclaration declaration = parsePipeline(packagingStep(target));
        PackagingStep step = assertInstanceOf(
                PackagingStep.class,
                declaration.steps().getLast()
        );
        assertEquals("Publish Containers", step.name());
        assertEquals("package-oci-image-to-ghcr", step.operation());
        assertEquals("PACKAGE_APP_ID", step.clientIdVariable());
        assertEquals("PACKAGE_APP_KEY", step.privateKeySecret());
        assertEquals("package-owner", step.ghcrUsername());
        assertEquals("PACKAGE_GHCR_PAT", step.ghcrPATSecret());
        assertEquals(List.of(new ReleaseSource("owner/pure-java", "main", platform)), step.targets());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null",
        "[]",
        "[null]",
        "[{}]",
        "[{\"repositoryName\":\"invalid\",\"branch\":\"main\",\"platform\":\"aarch64-linux\"}]",
        "[{\"repositoryName\":\"owner/pure-java\",\"branch\":\"\",\"platform\":\"aarch64-linux\"}]",
        "[{\"repositoryName\":\"owner/pure-java\",\"branch\":\"main\",\"platform\":\"unsupported\"}]"
    })
    void rejectsMissingOrInvalidTargets(String targets) {
        assertThrows(IllegalArgumentException.class, () -> parsePipeline(packagingStep(targets)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\" \""})
    void requiresPackagingNameAndGHCRUsername(String name) {
        String targets = """
                [{"repositoryName":"owner/pure-java","branch":"main","platform":"aarch64-linux"}]
                """;
        String step = packagingStep(targets);
        assertThrows(IllegalArgumentException.class, () -> parsePipeline(step.replace("\"Publish Containers\"", name)));
        assertThrows(IllegalArgumentException.class, () -> parsePipeline(step.replace("\"package-owner\"", name)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\" \"", "\"1APP\"", "\"APP-ID\""})
    void requiresValidCredentialReferences(String reference) {
        String targets = """
                [{"repositoryName":"owner/pure-java","branch":"main","platform":"aarch64-linux"}]
                """;
        String step = packagingStep(targets);
        assertThrows(
                IllegalArgumentException.class,
                () -> parsePipeline(step.replace("\"PACKAGE_APP_ID\"", reference))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> parsePipeline(step.replace("\"PACKAGE_APP_KEY\"", reference))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parsePipeline(step.replace("\"PACKAGE_GHCR_PAT\"", reference))
        );
    }

    private String packagingStep(String targets) {
        return """
                {"name":"Publish Containers","operation":"package-oci-image-to-ghcr",
                 "clientIdVariable":"PACKAGE_APP_ID","privateKeySecret":"PACKAGE_APP_KEY",
                 "ghcrUsername":"package-owner","ghcrPATSecret":"PACKAGE_GHCR_PAT","targets":%s}
                """.replace("%s", targets);
    }

    private LivePipelineDeclaration parsePipeline(String step) {
        String json = """
                {"repositories":[{"repositoryName":"owner/pure-java","branch":"main",
                  "build_system":"universal","platforms":["aarch64-linux","x86_64-linux"]}],
                 "steps":[{"name":"Build","operation":"build",
                  "clientIdVariable":"APP_ID","privateKeySecret":"APP_KEY"},%s]}
                """.replace("%s", step);
        return JsonSanitizer.parseAndValidate(json, LivePipelineDeclaration.class);
    }
}
