package io.materializer.shared.livepipelineinputsstore.models;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PipelineInputsRepository(
        @NotNull
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String livePipelineInputsStoreRepository
) { }
