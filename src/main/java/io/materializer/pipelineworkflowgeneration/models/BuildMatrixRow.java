package io.materializer.pipelineworkflowgeneration.models;

public record BuildMatrixRow(
        String repositoryName,
        String commitSHA,
        String buildSystem,
        String platform,
        String runner
) { }
