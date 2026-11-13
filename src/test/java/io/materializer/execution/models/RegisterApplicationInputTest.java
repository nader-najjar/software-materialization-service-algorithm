package io.materializer.execution.models;
import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.shared.utilities.JsonSanitizer;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegisterApplicationInputTest {

    @Test
    void readsRegistrationInput() {
        RegisterApplicationInput input = JsonSanitizer.parseAndValidate(
                "{\"repositoryName\":\"owner/materializer\"}",
                RegisterApplicationInput.class
        );

        assertEquals("owner/materializer", input.repositoryName());
        assertEquals(1, JsonMapperUtility.MAPPER.valueToTree(input).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"repositoryName\":null}",
        "{\"repositoryName\":\"\"}",
        "{\"repositoryName\":\" \"}",
        "{\"repositoryName\":\"repo\"}",
        "{\"repositoryName\":\"owner/\"}",
        "{\"repositoryName\":\"/repo\"}",
        "{\"repositoryName\":\"owner/repo/extra\"}"
    })
    void registrationRequiresAnOwnerAndRepository(String json) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RegisterApplicationInput.class)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "123", "[]", "{}"})
    void rejectsNonStringRepositoryNames(String repositoryName) {
        String json = "{\"repositoryName\":%s}".formatted(repositoryName);

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSanitizer.parseAndValidate(json, RegisterApplicationInput.class)
        );
    }

}
