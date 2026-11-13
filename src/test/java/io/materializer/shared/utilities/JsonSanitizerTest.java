package io.materializer.shared.utilities;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.materializer.execution.models.RegisterApplicationInput;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonSanitizerTest {

    private static final String INPUT_JSON = "{\"repositoryName\":%s}";

    private static final String VALID_OPERATION = "\"owner/materializer\"";

    @Test
    void rejectsNullJSON() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(null, RegisterApplicationInput.class)
        );
    }

    @Test
    void readsTheRequestedRepositoryName() {
        String json = INPUT_JSON.formatted(VALID_OPERATION);
        RegisterApplicationInput input = JsonSanitizer.parseAndValidate(json, RegisterApplicationInput.class);

        assertEquals("owner/materializer", input.repositoryName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{",
        "[]",
        "{\"repositoryName\":\"owner/materializer\",\"repositoryName\":\"other\"}"
    })
    void rejectsMalformedAndDuplicateJSON(String json) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RegisterApplicationInput.class)
        );

        assertInstanceOf(JsonProcessingException.class, exception.getCause());
    }

    @Test
    void rejectsMissingRepositoryNameThroughSharedValidation() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate("{}", RegisterApplicationInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @Test
    void rejectsTrailingJSON() {
        String json = INPUT_JSON.formatted(VALID_OPERATION) + " {}";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RegisterApplicationInput.class)
        );

        assertInstanceOf(JsonProcessingException.class, exception.getCause());
    }

    @Test
    void rejectsNullInputThroughSharedValidation() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate("null", RegisterApplicationInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\" \""})
    void rejectsNullOrBlankRepositoryName(String repositoryName) {
        String json = INPUT_JSON.formatted(repositoryName);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RegisterApplicationInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

}
