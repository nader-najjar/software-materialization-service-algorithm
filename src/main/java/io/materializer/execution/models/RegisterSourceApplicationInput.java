package io.materializer.execution.models;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RegisterSourceApplicationInput(
        @JsonProperty("materializerRepository")
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String materializerRepository,

        @JsonProperty("sourceRepository")
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String sourceRepository
) { }
