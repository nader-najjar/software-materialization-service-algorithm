package io.materializer.notifyserviceworkflowgeneration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.materializer.shared.utilities.YamlMapperUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyServiceWorkflowGeneratorTest {

    @Test
    void dispatchesWithoutInputs() throws JsonProcessingException {
        String yaml = new NotifyServiceWorkflowGenerator("owner/service").generate("development");
        JsonNode workflow = YamlMapperUtility.MAPPER.readTree(yaml);

        assertEquals("owner", workflow.at("/env/SERVICE_OWNER").asText());
        assertEquals("service", workflow.at("/env/SERVICE_REPOSITORY").asText());
        assertEquals("development", workflow.at("/on/push/branches/0").asText());
        assertTrue(workflow.required("permissions").isEmpty());
        JsonNode steps = workflow.at("/jobs/notify-service/steps");
        assertEquals("${{ secrets.MATERIALIZATION_DISPATCH_PRIVATE_KEY }}", steps.at("/0/with/private-key").asText());
        assertEquals("gh workflow run materialize-all-software.yml --repo \"$SERVICE_OWNER/$SERVICE_REPOSITORY\""
                + " --ref \"$SERVICE_REF\"", steps.at("/1/run").asText());
    }
}
