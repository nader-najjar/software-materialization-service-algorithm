package io.materializer.shared.livepipelineinputsstore.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.models.RepositorySnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record LivePipelineInputs(

        @JsonProperty("materializerRepositorySnapshot")
        @NotNull
        @Valid
        RepositorySnapshot materializerRepositorySnapshot,

        @JsonProperty("declaredRepositorySnapshots")
        @NotNull
        List<@NotNull @Valid RepositorySnapshot> declaredRepositorySnapshots

) {
    @AssertTrue(message = "Each repository and branch pair must be unique")
    @JsonIgnore
    public boolean isRepositoryBranchListUnique() {
        if (declaredRepositorySnapshots == null) { return true; }

        Set<List<String>> repositoryBranchPairs = new HashSet<>();
        for (RepositorySnapshot snapshot : declaredRepositorySnapshots) {
            if (snapshot == null || snapshot.repositoryName() == null || snapshot.branch() == null) { continue; }

            String repositoryName = snapshot.repositoryName().toLowerCase(Locale.ROOT);
            if (!repositoryBranchPairs.add(List.of(repositoryName, snapshot.branch()))) { return false; }
        }

        return true;
    }

    public LivePipelineInputs {
        declaredRepositorySnapshots = declaredRepositorySnapshots == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(declaredRepositorySnapshots));
    }
}
