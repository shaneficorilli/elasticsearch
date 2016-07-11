/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.monitoring.license;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.license.core.License;
import org.elasticsearch.license.plugin.Licensing;
import org.elasticsearch.license.plugin.core.LicenseState;
import org.elasticsearch.license.plugin.core.Licensee;
import org.elasticsearch.license.plugin.core.LicenseeRegistry;
import org.elasticsearch.license.plugin.core.LicensesManagerService;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.elasticsearch.xpack.XPackPlugin;
import org.elasticsearch.xpack.monitoring.MonitoringLicensee;
import org.elasticsearch.xpack.monitoring.test.MonitoringIntegTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.elasticsearch.test.ESIntegTestCase.Scope.SUITE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;

@ClusterScope(scope = SUITE, transportClientRatio = 0, numClientNodes = 0)
public class LicenseIntegrationTests extends MonitoringIntegTestCase {
    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(InternalXPackPlugin.class);
    }

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder()
                .put(super.nodeSettings(nodeOrdinal))
                .build();
    }

    public void testEnableDisableLicense() {
        assertThat(getLicensee().getStatus().getLicenseState(), isOneOf(LicenseState.ENABLED, LicenseState.GRACE_PERIOD));
        assertThat(getLicensee().collectionEnabled(), is(true));
        disableLicensing();

        assertThat(getLicensee().getStatus().getLicenseState(), equalTo(LicenseState.DISABLED));
        assertThat(getLicensee().collectionEnabled(), is(false));
        enableLicensing();

        assertThat(getLicensee().getStatus().getLicenseState(), isOneOf(LicenseState.ENABLED, LicenseState.GRACE_PERIOD));
        assertThat(getLicensee().collectionEnabled(), is(true));
    }

    private MonitoringLicensee getLicensee() {
        MonitoringLicensee licensee = internalCluster().getInstance(MonitoringLicensee.class);
        assertNotNull(licensee);
        return licensee;
    }

    public static void disableLicensing() {
        for (MockLicenseService service : internalCluster().getInstances(MockLicenseService.class)) {
            service.disable();
        }
    }

    public static void enableLicensing() {
        for (MockLicenseService service : internalCluster().getInstances(MockLicenseService.class)) {
            service.enable();
        }
    }

    public static class MockLicensing extends Licensing {

        public MockLicensing() {
            super(Settings.EMPTY);
        }

        @Override
        public Collection<Module> nodeModules() {
            return Collections.<Module>singletonList(new InternalLicenseModule());
        }

        @Override
        public List<ActionHandler<? extends ActionRequest<?>, ? extends ActionResponse>> getActions() {
            return emptyList();
        }

        @Override
        public List<Class<? extends RestHandler>> getRestHandlers() {
            return emptyList();
        }

        @Override
        public Collection<Class<? extends LifecycleComponent>> nodeServices() {
            return Collections.emptyList();
        }

    }

    public static class InternalLicenseModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(MockLicenseService.class).asEagerSingleton();
            bind(LicenseeRegistry.class).to(MockLicenseService.class);
            bind(LicensesManagerService.class).to(MockLicenseService.class);
        }
    }

    public static class MockLicenseService extends AbstractComponent implements LicenseeRegistry, LicensesManagerService {

        private final List<Licensee> licensees = new ArrayList<>();

        @Inject
        public MockLicenseService(Settings settings) {
            super(settings);
            enable();
        }

        @Override
        public void register(Licensee licensee) {
            licensees.add(licensee);
            enable();
        }

        public void enable() {
            for (Licensee licensee : licensees) {
                licensee.onChange(new Licensee.Status(License.OperationMode.BASIC,
                        randomBoolean() ? LicenseState.ENABLED : LicenseState.GRACE_PERIOD));
            }
        }

        public void disable() {
            for (Licensee licensee : licensees) {
                licensee.onChange(new Licensee.Status(License.OperationMode.BASIC, LicenseState.DISABLED));
            }
        }

        @Override
        public LicenseState licenseState() {
            return null;
        }

        @Override
        public License getLicense() {
            return null;
        }
    }

    public static class InternalXPackPlugin extends XPackPlugin {
        public InternalXPackPlugin(Settings settings) throws IOException {
            super(settings);
            licensing = new MockLicensing();
        }
    }
}
