package io.materializer.pipelineworkflowgeneration;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import io.materializer.pipelineworkflowgeneration.models.BuildMatrixRow;
import io.materializer.shared.livepipelinedeclarationfetcher.LivePipelineDeclarationFetcher;
import io.materializer.shared.livepipelinedeclarationfetcher.models.DeclaredPipelineStep;
import io.materializer.shared.livepipelinedeclarationfetcher.models.LivePipelineDeclaration;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.utilities.ResourceUtility;
import io.materializer.shared.utilities.YamlMapperUtility;

public class PipelineWorkflowGenerator {

    private final LivePipelineDeclarationFetcher livePipelineDeclarationFetcher;
    private final BuildMatrixGenerator buildMatrixGenerator;

    @Inject
    public PipelineWorkflowGenerator(
            LivePipelineDeclarationFetcher livePipelineDeclarationFetcher,
            BuildMatrixGenerator buildMatrixGenerator
    ) {
        this.livePipelineDeclarationFetcher = livePipelineDeclarationFetcher;
        this.buildMatrixGenerator = buildMatrixGenerator;
    }

    public String generate(LivePipelineInputs inputs) {
        LivePipelineDeclaration declaration = livePipelineDeclarationFetcher.fetchAndValidate(
                inputs.materializerRepositorySnapshot()
        );
        String template = ResourceUtility.readString("/pipelineworkflowgeneration/materialize-template.yml");
        try {
            ObjectNode workflow = (ObjectNode) YamlMapperUtility.MAPPER.readTree(template);
            ObjectNode build = (ObjectNode) workflow.required("jobs").required("build");
            DeclaredPipelineStep buildStep = declaration.steps().getFirst();
            build.put("name", buildStep.name()
                    + " ${{ matrix.repositoryName }} (${{ matrix.platform }})");
            ObjectNode buildEnvironment = (ObjectNode) build.required("env");
            buildEnvironment.put("CLIENT_ID_VARIABLE", buildStep.clientIdVariable());
            buildEnvironment.put("PRIVATE_KEY_SECRET", buildStep.privateKeySecret());
            ObjectNode matrix = (ObjectNode) build.required("strategy").required("matrix");
            List<BuildMatrixRow> builds = buildMatrixGenerator.generate(
                    declaration.repositories(), inputs.declaredRepositorySnapshots()
            );
            matrix.set("include", YamlMapperUtility.MAPPER.valueToTree(builds));
            return YamlMapperUtility.MAPPER.writeValueAsString(workflow);

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not generate materialization workflow", exception);
        }
    }
}
