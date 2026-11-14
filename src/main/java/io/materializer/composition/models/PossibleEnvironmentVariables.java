package io.materializer.composition.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PossibleEnvironmentVariables(
        @NotBlank
        String githubToken,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String materializerRegistryRepository,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String pipelineInputsRepository,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String materializationServiceRepository,

        @NotBlank
        String materializationDispatchClientID,

        @NotBlank
        String materializationDispatchPrivateKey,

        @NotBlank
        String materializationPublishClientID,

        @NotBlank
        String materializationPublishPrivateKey
) {
    @Override
    public String toString() {
        return "PossibleEnvironmentVariables[REDACTED]";
    }
}
