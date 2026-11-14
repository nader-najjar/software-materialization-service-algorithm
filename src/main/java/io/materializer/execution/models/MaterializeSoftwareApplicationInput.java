package io.materializer.execution.models;

import io.materializer.shared.models.PushEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MaterializeSoftwareApplicationInput(
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String pipelineRepository,

        @NotNull
        @Valid
        PushEvent pushEvent
) { }
