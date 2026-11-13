package io.materializer.composition;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistryRepository;
import io.materializer.shared.utilities.HibernateValidatorUtility;

public class MaterializerRegistryRepositoryModule extends AbstractModule {

    @Provides
    @Singleton
    MaterializerRegistryRepository provideMaterializerRegistryRepository() {
        MaterializerRegistryRepository materializerRegistryRepository = new MaterializerRegistryRepository(
                System.getenv("MATERIALIZER_REGISTRY_REPOSITORY")
        );
        HibernateValidatorUtility.validate(materializerRegistryRepository);
        return materializerRegistryRepository;
    }
}
