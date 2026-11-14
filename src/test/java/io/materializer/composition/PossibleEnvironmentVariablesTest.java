package io.materializer.composition;

import io.materializer.composition.models.PossibleEnvironmentVariables;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PossibleEnvironmentVariablesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    void rejectsMissingValues(String value) {
        PossibleEnvironmentVariables environmentVariables =
                new PossibleEnvironmentVariables(value, null, null, null, null, null, null, null);
        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validateProperties(
                environmentVariables,
                "githubToken"
        ));
    }

    @Test
    void validatesOnlyTheSelectedProperty() {
        PossibleEnvironmentVariables environmentVariables =
                new PossibleEnvironmentVariables(null, null, "owner/repo", null, null, null, null, null);
        assertDoesNotThrow(() -> HibernateValidatorUtility.validateProperties(
                environmentVariables,
                "pipelineInputsRepository"
        ));
    }

    @Test
    void rejectsInvalidRepositoriesWithoutExposingTheValue() {
        PossibleEnvironmentVariables environmentVariables =
                new PossibleEnvironmentVariables(null, null, "invalid-value", null, null, null, null, null);
        ValidationException exception = assertThrows(ValidationException.class,
                () -> HibernateValidatorUtility.validateProperties(environmentVariables, "pipelineInputsRepository"));
        assertFalse(exception.getMessage().contains("invalid-value"));
    }
}
