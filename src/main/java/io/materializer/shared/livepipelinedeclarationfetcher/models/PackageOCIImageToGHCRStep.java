package io.materializer.shared.livepipelinedeclarationfetcher.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PackageOCIImageToGHCRStep(

        @JsonProperty("name")
        @NotBlank
        String name,

        @JsonProperty("operation")
        @NotNull
        @Pattern(regexp = "package-oci-image-to-ghcr")
        String operation,

        @JsonProperty("clientIdVariable")
        @NotNull
        @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]*")
        String clientIdVariable,

        @JsonProperty("privateKeySecret")
        @NotNull
        @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]*")
        String privateKeySecret,

        @JsonProperty("targets")
        @NotEmpty
        List<@NotNull @Valid ReleaseSource> targets

) implements DeclaredPipelineStep {
    public PackageOCIImageToGHCRStep {
        targets = targets == null ? null : Collections.unmodifiableList(new ArrayList<>(targets));
    }
}
