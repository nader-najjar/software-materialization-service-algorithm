package io.materializer.shared.livepipelinedeclarationfetcher.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.livepipelinedeclarationfetcher.models.annotations.UniquePipelineInputsByRepositoryAndBranch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LivePipelineDeclaration(

        @JsonProperty("repositories")
        @NotNull
        @UniquePipelineInputsByRepositoryAndBranch
        List<@NotNull @Valid DeclaredPipelineInput> repositories,

        @JsonProperty("steps")
        @NotNull
        @Size(max = 1)
        List<@NotNull @Valid DeclaredPipelineStep> steps

) {
    @AssertTrue(message = "repositories and steps must both be empty or both be non-empty")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isPipelineShapeValid() {
        return repositories == null || steps == null || repositories.isEmpty() == steps.isEmpty();
    }

    public LivePipelineDeclaration {
        repositories = repositories == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(repositories));
        steps = steps == null ? null : Collections.unmodifiableList(new ArrayList<>(steps));
    }
}
