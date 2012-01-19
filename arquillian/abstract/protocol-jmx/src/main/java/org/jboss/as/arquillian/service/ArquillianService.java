/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.arquillian.service;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;

import org.jboss.arquillian.protocol.jmx.JMXTestRunner;
import org.jboss.as.jmx.MBeanServerService;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.SetupAction;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import static org.jboss.as.server.deployment.Services.JBOSS_DEPLOYMENT;

/**
 * Service responsible for creating and managing the life-cycle of the Arquillian service.
 *
 * @author Thomas.Diesler@jboss.com
 * @author Kabir Khan
 * @since 17-Nov-2010
 */
public abstract class ArquillianService<C extends ArquillianConfig> {

    public static final String TEST_CLASS_PROPERTY = "org.jboss.as.arquillian.testClass";
    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("arquillian", "testrunner");

    private static final Logger log = Logger.getLogger("org.jboss.as.arquillian");

    /*
     * Note: Do not put direct class references on JUnit or TestNG here; this
     * must be compatible with both without resulting in NCDFE
     *
     * AS7-1303
     */

    private static final String CLASS_NAME_JUNIT_RUNNER = "org.junit.runner.RunWith";

    private static final String CLASS_NAME_TESTNG_RUNNER = "org.jboss.arquillian.testng.Arquillian";

    private final InjectedValue<MBeanServer> injectedMBeanServer = new InjectedValue<MBeanServer>();
    private final Set<ArquillianConfig> deployedTests = new HashSet<ArquillianConfig>();
    private ServiceContainer serviceContainer;
    private ServiceTarget serviceTarget;
    private JMXTestRunner jmxTestRunner;
    AbstractServiceListener<Object> listener;

    protected <T extends ArquillianService> void build(final ServiceBuilder<T> serviceBuilder) {
        serviceBuilder.addDependency(MBeanServerService.SERVICE_NAME, MBeanServer.class, this.injectedMBeanServer);
    }

    protected abstract C createArquillianConfig(final DeploymentUnit depUnit, final Set<String> testClasses);

    protected JMXTestRunner.TestClassLoader createTestClassLoader() {
        return new ExtendedTestClassLoader();
    }

    protected ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

    protected ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    private C processDeployment(final DeploymentUnit depUnit) {

        final CompositeIndex compositeIndex = depUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if(compositeIndex == null) {
            log.warnf("Cannot find composite annotation index in: %s", depUnit);
            return null;
        }

        // Got JUnit?
        final DotName runWithName = DotName.createSimple(CLASS_NAME_JUNIT_RUNNER);
        final List<AnnotationInstance> runWithList = compositeIndex.getAnnotations(runWithName);

        // Got TestNG?
        final DotName testNGClassName = DotName.createSimple(CLASS_NAME_TESTNG_RUNNER);
        final Set<ClassInfo> testNgTests = compositeIndex.getAllKnownSubclasses(testNGClassName);

        // Get Test Class Names
        final Set<String> testClasses = new HashSet<String>();
        // JUnit
        for (AnnotationInstance instance : runWithList) {
            final AnnotationTarget target = instance.target();
            if (target instanceof ClassInfo) {
                final ClassInfo classInfo = (ClassInfo) target;
                final String testClassName = classInfo.name().toString();
                testClasses.add(testClassName);
            }
        }
        // TestNG
        for(final ClassInfo classInfo : testNgTests){
            testClasses.add(classInfo.name().toString());
        }

        // No tests found
        if (testClasses.isEmpty()) {
            return null;
        }

        // FIXME: Why do we get another service started event from a deployment INSTALLED service?
        C arqConfig = createArquillianConfig(depUnit, testClasses);
        if (getServiceContainer().getService(arqConfig.getServiceName()) != null) {
            log.warnf("Arquillian config already registered: %s", arqConfig);
            return null;
        }

        depUnit.putAttachment(ArquillianConfig.KEY, arqConfig);
        return arqConfig;
    }

    public synchronized void start(StartContext context) throws StartException {
        log.debugf("Starting Arquillian Test Runner");

        final MBeanServer mbeanServer = injectedMBeanServer.getValue();
        serviceContainer = context.getController().getServiceContainer();
        serviceTarget = context.getChildTarget();
        try {
            jmxTestRunner = new ExtendedJMXTestRunner();
            jmxTestRunner.registerMBean(mbeanServer);
        } catch (Throwable t) {
            throw new StartException("Failed to start Arquillian Test Runner", t);
        }

        listener = new AbstractServiceListener<Object>() {

            @Override
            public void transition(ServiceController<? extends Object> serviceController, ServiceController.Transition transition) {
                switch (transition) {
                    case STARTING_to_UP: {
                        ServiceName serviceName = serviceController.getName();
                        String simpleName = serviceName.getSimpleName();
                        if (JBOSS_DEPLOYMENT.isParentOf(serviceName) && simpleName.equals(Phase.INSTALL.toString())) {
                            ServiceName parentName = serviceName.getParent();
                            ServiceController<?> parentController = serviceContainer.getService(parentName);
                            DeploymentUnit depUnit = (DeploymentUnit) parentController.getValue();
                            C arqConfig = processDeployment(depUnit);
//                            C arqConfig = ArquillianConfigBuilder.processDeployment(arqService, depUnit);
                            if (arqConfig != null) {
                                log.infof("Arquillian deployment detected: %s", arqConfig);
                                ServiceBuilder<C> builder = arqConfig.buildService(serviceTarget, serviceController);
                                startingToUp(builder, arqConfig);
                                builder.install();
                            }
                        }

                    }
                }
            }
        };
        serviceContainer.addListener(listener);
    }

    protected abstract void startingToUp(final ServiceBuilder<C> builder, final C arqConfig);

    public synchronized void stop(StopContext context) {
        log.debugf("Stopping Arquillian Test Runner");
        try {
            if (jmxTestRunner != null) {
                jmxTestRunner.unregisterMBean(injectedMBeanServer.getValue());
            }
        } catch (Exception ex) {
            log.errorf(ex, "Cannot stop Arquillian Test Runner");
        } finally {
            context.getController().getServiceContainer().removeListener(listener);
        }
    }

    void registerArquillianConfig(ArquillianConfig arqConfig) {
        synchronized (deployedTests) {
            log.debugf("Register Arquillian config: %s", arqConfig.getServiceName());
            deployedTests.add(arqConfig);
            deployedTests.notifyAll();
        }
    }

    void unregisterArquillianConfig(ArquillianConfig arqConfig) {
        synchronized (deployedTests) {
            log.debugf("Unregister Arquillian config: %s", arqConfig.getServiceName());
            deployedTests.remove(arqConfig);
        }
    }

    protected C getArquillianConfig(final String className, long timeout) {
        synchronized (deployedTests) {

            log.debugf("Getting Arquillian config for: %s", className);
            for (ArquillianConfig<?> arqConfig : deployedTests) {
                for (String aux : arqConfig.getTestClasses()) {
                    if (aux.equals(className)) {
                        log.debugf("Found Arquillian config for: %s", className);
                        // TODO: is this cast valid?
                        return (C) arqConfig;
                    }
                }
            }

            if (timeout <= 0) {
                throw new IllegalStateException("Cannot obtain Arquillian config for: " + className);
            }

            try {
                log.debugf("Waiting on Arquillian config for: %s", className);
                deployedTests.wait(timeout);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return getArquillianConfig(className, -1);
    }

    /*
    void registerArquillianServiceWithOSGi(BundleManagerService bundleManager) {
        ModuleClassLoader classLoader = ((ModuleClassLoader) ArquillianService.class.getClassLoader());
        Module module = classLoader.getModule();
        if (bundleManager.getBundle(module.getIdentifier()) == null) {
            OSGiMetaDataBuilder builder = OSGiMetaDataBuilder.createBuilder("arquillian-service");
            builder.addExportPackages("org.jboss.arquillian.container.test.api", "org.jboss.arquillian.junit");
            builder.addExportPackages("org.jboss.arquillian.osgi", "org.jboss.arquillian.test.api");
            builder.addExportPackages("org.jboss.shrinkwrap.api", "org.jboss.shrinkwrap.api.asset", "org.jboss.shrinkwrap.api.spec");
            builder.addExportPackages("org.junit", "org.junit.runner");
            try {
                log.infof("Register arquillian service with OSGi layer");
                bundleManager.registerModule(serviceTarget, module, builder.getOSGiMetaData());
            } catch (BundleException ex) {
                log.errorf(ex, "Cannot register arquillian service with OSGi layer");
            }
        }
    }
    */

    class ExtendedJMXTestRunner extends JMXTestRunner {

        ExtendedJMXTestRunner() {
            super(createTestClassLoader());
        }

        @Override
        public byte[] runTestMethod(final String className, final String methodName) {
            ArquillianConfig arqConfig = getArquillianConfig(className, 30000L);
            Map<String, Object> properties = Collections.<String, Object> singletonMap(TEST_CLASS_PROPERTY, className);
            ContextManager contextManager = initializeContextManager(arqConfig, properties);
            try {
                return super.runTestMethod(className, methodName);
            } finally {
                contextManager.teardown(properties);
            }
        }

        private ContextManager initializeContextManager(final ArquillianConfig config, final Map<String, Object> properties) {
            final ContextManagerBuilder builder = new ContextManagerBuilder();
            final DeploymentUnit deployment = config.getDeploymentUnit();
            final Module module = deployment.getAttachment(Attachments.MODULE);
            if (module != null) {
                builder.add(new TCCLSetupAction(module.getClassLoader()));
            }
            builder.addAll(deployment);
            ContextManager contextManager = builder.build();
            contextManager.setup(properties);
            return contextManager;
        }
    }

    class ExtendedTestClassLoader implements JMXTestRunner.TestClassLoader {

        @Override
        public Class<?> loadTestClass(final String className) throws ClassNotFoundException {

            final ArquillianConfig arqConfig = getArquillianConfig(className, -1);
            if (arqConfig == null)
                throw new ClassNotFoundException("No Arquillian config found for: " + className);

            return arqConfig.loadClass(className);
        }
    }

    /**
     * Sets and restores the Thread Context ClassLoader
     *
     * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
     * @author Stuart Douglas
     */
    private static final class TCCLSetupAction implements SetupAction {
        private final ThreadLocal<ClassLoader> oldClassLoader = new ThreadLocal<ClassLoader>();

        private final ClassLoader classLoader;

        TCCLSetupAction(final ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public int priority() {
            return 10000;
        }

        @Override
        public Set<ServiceName> dependencies() {
            return Collections.emptySet();
        }

        @Override
        public void setup(Map<String, Object> properties) {
            oldClassLoader.set(getTccl());
            setTccl(classLoader);
        }

        @Override
        public void teardown(Map<String, Object> properties) {
            ClassLoader old = oldClassLoader.get();
            oldClassLoader.remove();
            setTccl(old);
        }
    }

    /**
     * {@link PrivilegedAction} implementation to get at the TCCL
     *
     * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
     */
    private enum GetTcclAction implements PrivilegedAction<ClassLoader> {
        INSTANCE;

        @Override
        public ClassLoader run() {
            return Thread.currentThread().getContextClassLoader();
        }
    }

    private static ClassLoader getTccl() {
        return AccessController.doPrivileged(GetTcclAction.INSTANCE);
    }

    private static void setTccl(final ClassLoader cl) {
        assert cl != null : "ClassLoader must be specified";
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                Thread.currentThread().setContextClassLoader(cl);
                return null;
            }
        });
    }
}
