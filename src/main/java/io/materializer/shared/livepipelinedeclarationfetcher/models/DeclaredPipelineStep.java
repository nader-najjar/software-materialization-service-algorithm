package io.materializer.shared.livepipelinedeclarationfetcher.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.materializer.shared.utilities.JsonMapperUtility;

public interface DeclaredPipelineStep {

    String name();

    String operation();

    @JsonCreator
    static DeclaredPipelineStep fromJson(JsonNode step) throws JsonProcessingException {
        String operation = step.path("operation").asText();
        switch (operation) {
            case "build":
                return JsonMapperUtility.MAPPER.treeToValue(step, BuildStep.class);
            case "run-composite-action":
                return JsonMapperUtility.MAPPER.treeToValue(step, RunCompositeActionStep.class);
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }
}
