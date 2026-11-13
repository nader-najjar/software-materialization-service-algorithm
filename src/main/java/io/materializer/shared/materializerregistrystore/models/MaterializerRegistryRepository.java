package io.materializer.shared.materializerregistrystore.models;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MaterializerRegistryRepository(
        @NotNull
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String materializerRegistryRepository
) { }
