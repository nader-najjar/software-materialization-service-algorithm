package io.materializer.execution.models;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RegisterApplicationInput(

        @JsonProperty("repositoryName")
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String repositoryName,

        @JsonProperty("sourceRepositories")
        List<@NotNull @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") String> sourceRepositories

) {
    public RegisterApplicationInput {
        sourceRepositories = Collections.unmodifiableList(
                new ArrayList<>(sourceRepositories == null ? List.of() : sourceRepositories));
    }

    @JsonIgnore
    @AssertTrue(message = "sourceRepositories must contain unique repository names ignoring case")
    public boolean isSourceRepositoryListUnique() {
        Set<String> seen = new HashSet<>();
        for (String repository : sourceRepositories) {
            if (repository != null && !seen.add(repository.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }
}
