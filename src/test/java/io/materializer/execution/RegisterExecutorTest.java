package io.materializer.execution;
import java.util.List;

import io.materializer.shared.materializerregistrystore.MaterializerRegistryStore;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistration;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistry;
import io.materializer.shared.materializerregistrystore.models.MaterializerRegistrySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RegisterExecutorTest {

    private final MaterializerRegistryStore materializerRegistryStore = mock(MaterializerRegistryStore.class);
    private final RegisterExecutor registerExecutor = new RegisterExecutor(
            materializerRegistryStore
    );
    private static final String INPUT = "{\"repositoryName\":\"owner/materializer\"}";

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42", "{}", "{\"repositoryName\":42}", "{\"repositoryName\":\"invalid\"}"})
    void rejectsInvalidInputBeforeCheckingRegistration(String json) {
        assertThrows(IllegalArgumentException.class, () -> registerExecutor.execute(json));

        verifyNoInteractions(materializerRegistryStore);
    }

    @Test
    void checksRegistryWithoutWriting() {
        prepareRegistry(List.of());

        registerExecutor.execute(INPUT);

        verify(materializerRegistryStore).readSnapshot();
        verifyNoMoreInteractions(materializerRegistryStore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner/materializer", "OWNER/Materializer"})
    void rejectsExistingRegistration(String repositoryName) {
        prepareRegistry(List.of(new MaterializerRegistration(repositoryName)));

        assertThrows(IllegalStateException.class, () -> registerExecutor.execute(INPUT));

        verify(materializerRegistryStore).readSnapshot();
        verifyNoMoreInteractions(materializerRegistryStore);
    }

    private void prepareRegistry(List<MaterializerRegistration> registrations) {
        when(materializerRegistryStore.readSnapshot()).thenReturn(
                new MaterializerRegistrySnapshot(new MaterializerRegistry(registrations), "file-sha")
        );
    }
}
