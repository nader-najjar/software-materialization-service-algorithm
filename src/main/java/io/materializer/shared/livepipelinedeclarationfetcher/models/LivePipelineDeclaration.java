package io.materializer.shared.livepipelinedeclarationfetcher.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record LivePipelineDeclaration(

        @JsonProperty("repositories")
        @NotNull
        List<@NotNull @Valid DeclaredPipelineInput> repositories,

        @JsonProperty("steps")
        @NotNull
        List<@NotNull @Valid DeclaredPipelineStep> steps

) {
    @AssertTrue(message = "repositories and steps must both be empty or both be non-empty")
    @JsonIgnore
    public boolean isPipelineShapeValid() {
        return repositories == null || steps == null || repositories.isEmpty() == steps.isEmpty();
    }

    @AssertTrue(message = "Each repository and branch pair must be unique")
    @JsonIgnore
    public boolean isRepositoryBranchListUnique() {
        if (repositories == null) { return true; }

        Set<List<String>> repositoryBranchPairs = new HashSet<>();
        for (DeclaredPipelineInput input : repositories) {
            if (input == null || input.repositoryName() == null || input.branch() == null) { continue; }

            String repositoryName = input.repositoryName().toLowerCase(Locale.ROOT);
            if (!repositoryBranchPairs.add(List.of(repositoryName, input.branch()))) { return false; }
        }

        return true;
    }

    @AssertTrue(message = "the first step must be build")
    @JsonIgnore
    public boolean isFirstStepBuild() {
        if (steps == null || steps.isEmpty()) { return true; }
        return steps.getFirst() != null && "build".equals(steps.getFirst().operation());
    }

    @AssertTrue(message = "build may only appear first and packaging may only appear second")
    @JsonIgnore
    public boolean isBuildAndPackagingOrderValid() {
        if (steps == null) { return true; }

        return steps.stream().skip(1).noneMatch(BuildStep.class::isInstance)
                && steps.stream().skip(2).noneMatch(PackagingStep.class::isInstance);
    }

    public LivePipelineDeclaration {
        repositories = repositories == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(repositories));
        steps = steps == null ? null : Collections.unmodifiableList(new ArrayList<>(steps));
    }
}
