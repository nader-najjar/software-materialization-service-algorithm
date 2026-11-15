package io.materializer.shared.materializerregistrystore.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MaterializerRegistration(

        @JsonProperty("repositoryName")
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String repositoryName,

        @JsonProperty("sourceRepositories")
        List<@NotNull @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") String> sourceRepositories

) {
    public MaterializerRegistration {
        sourceRepositories = Collections.unmodifiableList(
                new ArrayList<>(sourceRepositories == null ? List.of() : sourceRepositories)
        );
    }
}
