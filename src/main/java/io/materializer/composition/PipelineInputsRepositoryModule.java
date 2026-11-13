package io.materializer.composition;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.materializer.shared.livepipelineinputsstore.models.PipelineInputsRepository;
import io.materializer.shared.utilities.HibernateValidatorUtility;

public class PipelineInputsRepositoryModule extends AbstractModule {

    @Provides
    @Singleton
    PipelineInputsRepository providePipelineInputsRepository() {
        PipelineInputsRepository pipelineInputsRepository = new PipelineInputsRepository(
                System.getenv("LIVE_PIPELINE_INPUTS_STORE_REPOSITORY")
        );
        HibernateValidatorUtility.validate(pipelineInputsRepository);
        return pipelineInputsRepository;
    }
}
