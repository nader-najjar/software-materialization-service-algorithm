package io.materializer.shared.livepipelinedeclarationfetcher.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ActionReplacement(

        @JsonProperty("artifactKind")
        @NotBlank
        @Pattern(regexp = "release-artifact|oci-image")
        String artifactKind,

        @JsonProperty("repositoryName")
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String repositoryName,

        @JsonProperty("branch")
        @NotBlank
        String branch,

        @JsonProperty("platform")
        @NotBlank
        @Pattern(regexp = "aarch64-linux|x86_64-linux")
        String platform

) { }
