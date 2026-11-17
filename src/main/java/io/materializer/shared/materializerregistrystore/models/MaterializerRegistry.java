package io.materializer.shared.materializerregistrystore.models;

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

public record MaterializerRegistry(

        @JsonProperty("registrations")
        @NotNull
        List<@NotNull @Valid MaterializerRegistration> registrations

) {
    @AssertTrue(message = "Materializer repositories must be unique regardless of case")
    @JsonIgnore
    public boolean isRegistrationListUnique() {
        if (registrations == null) { return true; }

        Set<String> seen = new HashSet<>();
        for (MaterializerRegistration registration : registrations) {
            if (registration == null || registration.repositoryName() == null) { continue; }
            if (!seen.add(registration.repositoryName().toLowerCase(Locale.ROOT))) { return false; }
        }
        return true;
    }

    public MaterializerRegistry {
        registrations = registrations == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(registrations));
    }
}
