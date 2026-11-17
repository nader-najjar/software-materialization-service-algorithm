package io.materializer.pipelineworkflowgeneration.models;

public record PackagingMatrixRow(
        String repositoryName,
        String commitSHA,
        String platform,
        String runner
) { }
