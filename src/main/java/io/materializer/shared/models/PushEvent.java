package io.materializer.shared.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PushEvent(
        @NotNull
        @Valid
        Repository repository,

        @NotNull
        @Pattern(regexp = "refs/heads/.+")
        String ref,

        @NotNull
        @Pattern(regexp = "[0-9a-f]{40}")
        String before,

        @NotNull
        @Pattern(regexp = "[0-9a-f]{40}")
        String after,

        @NotNull
        Boolean deleted
) {
    public record Repository(
            @JsonProperty("full_name")
            @NotNull
            @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
            String fullName
    ) { }
}
