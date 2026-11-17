package io.materializer.shared.livepipelinedeclarationfetcher.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BuildStep(

        @JsonProperty("name")
        @NotBlank
        String name,

        @JsonProperty("operation")
        @NotNull
        @Pattern(regexp = "build")
        String operation,

        @JsonProperty("clientIdVariable")
        @NotNull
        @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]*")
        String clientIdVariable,

        @JsonProperty("privateKeySecret")
        @NotNull
        @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]*")
        String privateKeySecret

) implements DeclaredPipelineStep { }
