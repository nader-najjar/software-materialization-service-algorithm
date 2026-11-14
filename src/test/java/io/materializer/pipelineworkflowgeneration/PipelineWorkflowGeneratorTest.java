package io.materializer.pipelineworkflowgeneration;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineInput;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.ResourceUtility;
import io.materializer.shared.utilities.YamlMapperUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineWorkflowGeneratorTest {

    @Test
    void generatesTheExpectedWorkflowYaml() throws JsonProcessingException {
        LivePipelineDeclarationFetcher fetcher = mock(LivePipelineDeclarationFetcher.class);
        PipelineWorkflowGenerator generator = new PipelineWorkflowGenerator(fetcher, new BuildMatrixGenerator());
        RepositorySnapshot materializer = new RepositorySnapshot("owner/mat", "main", "a".repeat(40));
        List<String> platforms = List.of("aarch64-linux", "x86_64-linux");
        List<DeclaredPipelineInput> repositories = List.of(
                new DeclaredPipelineInput("nader-najjar/pure-java", "main", "universal", platforms),
                new DeclaredPipelineInput("nader-najjar/pure-javascript", "main", "universal", platforms)
        );
        String javaSHA = "28e79fd674af4ba6387765247272fed25578430d";
        String javascriptSHA = "e90d63d9b81312d83738bb1654442d54ad40e36e";
        List<RepositorySnapshot> sources = List.of(
                new RepositorySnapshot("nader-najjar/pure-java", "main", javaSHA),
                new RepositorySnapshot("nader-najjar/pure-javascript", "main", javascriptSHA)
        );
        DeclaredPipelineStep step = new DeclaredPipelineStep("Build", "build");
        LivePipelineDeclaration declaration = new LivePipelineDeclaration(repositories, List.of(step));
        when(fetcher.fetchAndValidate(materializer)).thenReturn(declaration);

        String yaml = generator.generate(new LivePipelineInputs(materializer, sources));
        String expected = ResourceUtility.readString("/pipelineworkflowgeneration/materialize.yml");
        assertEquals(YamlMapperUtility.MAPPER.readTree(expected), YamlMapperUtility.MAPPER.readTree(yaml));
    }

}
