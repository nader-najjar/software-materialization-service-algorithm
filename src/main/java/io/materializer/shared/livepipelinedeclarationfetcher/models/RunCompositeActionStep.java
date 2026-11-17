package io.materializer.shared.livepipelinedeclarationfetcher.models;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RunCompositeActionStep(

        @JsonProperty("name")
        @NotBlank
        String name,

        @JsonProperty("operation")
        @NotNull
        @Pattern(regexp = "run-composite-action")
        String operation,

        @JsonProperty("actionBundlePath")
        @NotBlank
        String actionBundlePath,

        @JsonProperty("with")
        Map<@NotBlank String, @NotNull String> with,

        @JsonProperty("replacements")
        @NotNull
        Map<@NotBlank String, @NotNull @Valid ReleaseSource> replacements

) implements DeclaredPipelineStep {
    public RunCompositeActionStep {
        with = with == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(with));
        replacements = replacements == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(replacements));
    }
}
