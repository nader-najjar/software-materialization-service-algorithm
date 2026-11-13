package io.materializer.shared.models;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.materializer.shared.livepipelineinputsstore.models.PipelineInputsRepository;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistryRepository;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonMapperUtility;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityValuesTest {

    @Test
    void validatesIndependentValues() {
        assertDoesNotThrow(() -> HibernateValidatorUtility.validate(new GitHubToken("test-token")));
        assertDoesNotThrow(() -> HibernateValidatorUtility.validate(
                new MaterializerRegistryRepository("owner/registry")
        ));
        assertDoesNotThrow(() -> HibernateValidatorUtility.validate(
                new PipelineInputsRepository("owner/inputs")
        ));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingAndBlankCapabilityValues(String value) {
        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new GitHubToken(value))
        );
        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new MaterializerRegistryRepository(value))
        );
        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new PipelineInputsRepository(value))
        );
    }

    @Test
    void doesNotExposeTheToken() throws JsonProcessingException {
        GitHubToken gitHubToken = new GitHubToken("test-token");

        assertFalse(gitHubToken.toString().contains("test-token"));
        assertFalse(JsonMapperUtility.MAPPER.writeValueAsString(gitHubToken).contains("test-token"));
    }
}
