package io.materializer.notifyserviceworkflowgeneration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.materializer.shared.utilities.YamlMapperUtility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyServiceWorkflowGeneratorTest {

    @Test
    void preservesDispatchExpressionsAndForwardsThePushFile() throws JsonProcessingException {
        String yaml = new NotifyServiceWorkflowGenerator("owner/service").generate("owner/pipeline", "development");
        JsonNode workflow = YamlMapperUtility.MAPPER.readTree(yaml);

        assertEquals("owner", workflow.at("/env/SERVICE_OWNER").asText());
        assertEquals("service", workflow.at("/env/SERVICE_REPOSITORY").asText());
        assertEquals("owner/pipeline", workflow.at("/env/PIPELINE_REPOSITORY").asText());
        assertEquals("development", workflow.at("/on/push/branches/0").asText());
        assertTrue(workflow.required("permissions").isEmpty());
        JsonNode steps = workflow.at("/jobs/notify-service/steps");
        assertEquals("${{ secrets.MATERIALIZATION_DISPATCH_PRIVATE_KEY }}", steps.at("/0/with/private-key").asText());
        assertTrue(steps.at("/1/run").asText().contains("--field \"pushEvent=@$GITHUB_EVENT_PATH\""));
        assertTrue(steps.at("/1/run").asText().contains("materialize-software.yml"));
    }
}
