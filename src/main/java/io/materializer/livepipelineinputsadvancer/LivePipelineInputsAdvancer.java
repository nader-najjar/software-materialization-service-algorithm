package io.materializer.livepipelineinputsadvancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.materializer.shared.livepipelineinputsstore.LivePipelineInputsStore;
import io.materializer.shared.livepipelineinputsstore.models.LivePipelineInputs;
import io.materializer.shared.models.PushEvent;
import io.materializer.shared.models.RepositorySnapshot;
import io.materializer.shared.utilities.HibernateValidatorUtility;

public class LivePipelineInputsAdvancer {

    private final LivePipelineInputsStore livePipelineInputsStore;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Guice manages the shared inputs store")
    @Inject
    public LivePipelineInputsAdvancer(LivePipelineInputsStore livePipelineInputsStore) {
        this.livePipelineInputsStore = livePipelineInputsStore;
    }

    public LivePipelineInputs advance(String pipelineRepository, PushEvent push) {
        if (push.deleted() || push.after().equals("0".repeat(40))) {
            throw new IllegalArgumentException("Cannot materialize a deleted branch");
        }
        RepositorySnapshot updated = new RepositorySnapshot(
                push.repository().fullName(),
                push.ref().substring("refs/heads/".length()),
                push.after()
        );
        Optional<LivePipelineInputs> previous = livePipelineInputsStore.read(pipelineRepository);
        LivePipelineInputs next = previous.isEmpty()
                ? initialize(pipelineRepository, updated)
                : applyPush(previous.get(), updated);
        if (previous.isPresent() && next.equals(previous.get())) {
            return next;
        }
        HibernateValidatorUtility.validate(next);
        livePipelineInputsStore.write(next);
        return next;
    }

    private LivePipelineInputs initialize(String pipelineRepository, RepositorySnapshot updated) {
        if (!pipelineRepository.equalsIgnoreCase(updated.repositoryName())) {
            throw new IllegalStateException("The first push must come from the pipeline repository");
        }
        return new LivePipelineInputs(updated, List.of());
    }

    private LivePipelineInputs applyPush(LivePipelineInputs previous, RepositorySnapshot updated) {
        RepositorySnapshot materializer = previous.materializerRepositorySnapshot();
        if (matches(materializer, updated)) {
            return new LivePipelineInputs(
                    updated,
                    previous.declaredRepositorySnapshots()
            );
        }
        List<RepositorySnapshot> sources = new ArrayList<>(previous.declaredRepositorySnapshots());
        for (int index = 0; index < sources.size(); index++) {
            RepositorySnapshot source = sources.get(index);
            if (matches(source, updated)) {
                sources.set(index, updated);
                return new LivePipelineInputs(materializer, sources);
            }
        }
        throw new IllegalStateException("Push repository and branch are not tracked by this pipeline");
    }

    private boolean matches(RepositorySnapshot first, RepositorySnapshot second) {
        return first.repositoryName().equalsIgnoreCase(second.repositoryName())
                && first.branch().equals(second.branch());
    }
}
