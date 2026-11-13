package io.materializer.shared.models;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GitHubToken(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotNull
        @NotBlank
        String githubToken
) {
    @Override
    public String toString() {
        return "GitHubToken[REDACTED]";
    }
}
