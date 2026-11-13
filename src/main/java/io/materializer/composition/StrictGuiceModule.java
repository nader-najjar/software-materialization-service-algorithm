package io.materializer.composition;

import com.google.inject.AbstractModule;

public class StrictGuiceModule extends AbstractModule {

    @Override
    protected void configure() {
        binder().requireAtInjectOnConstructors();
        binder().disableCircularProxies();
    }
}
