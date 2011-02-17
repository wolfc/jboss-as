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

package org.jboss.as.ee.component;

import org.jboss.as.ee.component.injection.ComponentResourceInjectionConfiguration;
import org.jboss.as.ee.component.injection.MethodResourceInjection;
import org.jboss.as.ee.component.injection.ResourceInjectableConfiguration;
import org.jboss.as.ee.component.injection.ResourceInjection;
import org.jboss.as.ee.component.injection.ResourceInjectionDependency;
import org.jboss.as.ee.component.interceptor.ComponentInterceptorFactories;
import org.jboss.as.ee.component.interceptor.MethodInterceptorConfiguration;
import org.jboss.as.ee.component.lifecycle.ComponentLifecycle;
import org.jboss.as.ee.component.lifecycle.ComponentLifecycleConfiguration;
import org.jboss.as.ee.naming.ContextNames;
import org.jboss.as.naming.deployment.JndiName;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.Values;

import javax.naming.Context;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The configuration for a {@link Component} use for constructing and installing a component.
 *
 * @author John Bailey
 */
public class ComponentConfiguration extends ResourceInjectableConfiguration {
    private final String name;
    private final String componentClassName;
    private final ComponentFactory componentFactory;
    private final List<ComponentLifecycleConfiguration> postConstructConfiguration = new ArrayList<ComponentLifecycleConfiguration>();
    private final List<ComponentLifecycle> postConstructLifecycles = new ArrayList<ComponentLifecycle>();
    private final List<ComponentLifecycleConfiguration> preDestroyConfigurations = new ArrayList<ComponentLifecycleConfiguration>();
    private final List<ComponentLifecycle> preDestroyLifecycles = new ArrayList<ComponentLifecycle>();
    private final List<MethodInterceptorConfiguration> classInterceptorConfigurations = new ArrayList<MethodInterceptorConfiguration>();
    private final List<MethodInterceptorConfiguration> methodInterceptorConfigurations = new ArrayList<MethodInterceptorConfiguration>();
    private final List<MethodInterceptorConfiguration> componentInterceptorConfigurations = new ArrayList<MethodInterceptorConfiguration>();

    private final Set<ResourceInjectionDependency<?>> dependencies = new HashSet<ResourceInjectionDependency<?>>();
    private final ComponentInterceptorFactories componentInterceptorFactories = new ComponentInterceptorFactories();
    private final List<String> viewClassNames = new ArrayList<String>();
    private final List<InterceptorFactory> componentSystemInterceptorFactories = new ArrayList<InterceptorFactory>();

    private Class<?> componentClass;
    /**
     * Allow resource injection on the component itself.
     */
    private List<ComponentResourceInjectionConfiguration> componentResourceInjectionConfigs = new ArrayList<ComponentResourceInjectionConfiguration>();
    private List<ResourceInjection> componentResourceInjections = new ArrayList<ResourceInjection>();
    private ServiceName envContextServiceName;

    /**
     * Construct a new instance.
     *
     * @param name the EE component name
     * @param componentClassName the class name for the component
     * @param componentFactory the component factory to use to create the actual component
     */
    public ComponentConfiguration(final String name, final String componentClassName, final ComponentFactory componentFactory) {
        if (name == null) throw new IllegalArgumentException("Component name can not be null");
        this.name = name;
        if (componentClassName == null) throw new IllegalArgumentException("Component class can not be null");
        this.componentClassName = componentClassName;
        if (componentFactory == null) throw new IllegalArgumentException("Component factory can not be null");
        this.componentFactory = componentFactory;
    }

    /**
     * The component name.  This will often reflect the name of the EE component.
     *
     * @return The component name
     */
    public String getName() {
        return name;
    }

    /**
     * The component's class name.
     *
     * @return The bean class
     */
    public String getComponentClassName() {
        return componentClassName;
    }

    /**
     * The component factory
     *
     * @return The component factory
     */
    public ComponentFactory getComponentFactory() {
        return componentFactory;
    }

    /**
     * The component class
     *
     * @return The class
     */
    public Class<?> getComponentClass() {
        return componentClass;
    }

    /**
     * Set the component class
     *
     * @param componentClass the component class
     */
    public void setComponentClass(Class<?> componentClass) {
        this.componentClass = componentClass;
    }

    /**
     * The post-construct life-cycle methods.
     *
     * @return The post-construct life-cycle methods
     */
    public List<ComponentLifecycleConfiguration> getPostConstructLifecycleConfigurations() {
        return Collections.unmodifiableList(postConstructConfiguration);
    }

    /**
     * Add a post construct method to the configuration.
     *
     * @param postMethod The post-construct method
     */
    public void addPostConstructLifecycleConfiguration(final ComponentLifecycleConfiguration postMethod) {
        postConstructConfiguration.add(postMethod);
    }

    /**
     * The pre-destroy life-cycle methods.
     *
     * @return The pre-destroy life-cycle methods
     */
    public List<ComponentLifecycleConfiguration> getPreDestroyLifecycleConfigurations() {
        return Collections.unmodifiableList(preDestroyConfigurations);
    }

    /**
     * Add a pre-destroy method to the configuration.
     *
     * @param preDestroy The pre-destroy method
     */
    public void addPreDestroyLifecycleConfiguration(final ComponentLifecycleConfiguration preDestroy) {
        preDestroyConfigurations.add(preDestroy);
    }

    /**
     * The configurations for any class interceptors for this component type.
     *
     * @return The method interceptor configurations
     */
    public List<MethodInterceptorConfiguration> getClassInterceptorConfigs() {
        return Collections.unmodifiableList(classInterceptorConfigurations);
    }

    /**
     * Add a class interceptor configuration to the component configuration.
     *
     * @param interceptorConfiguration The interceptor configuration
     */
    public void addClassInterceptorConfig(final MethodInterceptorConfiguration interceptorConfiguration) {
        classInterceptorConfigurations.add(interceptorConfiguration);
    }

    /**
     * Add class interceptor configurations to the component configuration.
     *
     * @param interceptorConfigurations The interceptor configurations
     */
    public void addClassInterceptorConfigs(final MethodInterceptorConfiguration... interceptorConfigurations) {
        for (MethodInterceptorConfiguration config : interceptorConfigurations) {
            addClassInterceptorConfig(config);
        }
    }

    /**
     * Add class interceptor configurations to the component configuration.
     *
     * @param interceptorConfigurations The interceptor configurations
     */
    public void addClassInterceptorConfigs(final Collection<MethodInterceptorConfiguration> interceptorConfigurations) {
        this.classInterceptorConfigurations.addAll(interceptorConfigurations);
    }

    /**
     * The configurations for any method interceptors for this component type.
     *
     * @return The method interceptor configurations
     */
    public List<MethodInterceptorConfiguration> getMethodInterceptorConfigs() {
        return Collections.unmodifiableList(methodInterceptorConfigurations);
    }

    /**
     * Add a method interceptor configuration to the component configuration.
     *
     * @param interceptorConfiguration The interceptor configuration
     */
    public void addMethodInterceptorConfig(final MethodInterceptorConfiguration interceptorConfiguration) {
        methodInterceptorConfigurations.add(interceptorConfiguration);
    }

    /**
     * Add method interceptor configurations to the component configuration.
     *
     * @param interceptorConfigurations The interceptor configurations
     */
    public void addMethodInterceptorConfigs(final MethodInterceptorConfiguration... interceptorConfigurations) {
        for (MethodInterceptorConfiguration config : interceptorConfigurations) {
            addMethodInterceptorConfig(config);
        }
    }

    /**
     * Add method interceptor configurations to the component configuration.
     *
     * @param interceptorConfigurations The interceptor configurations
     */
    public void addMethodInterceptorConfigs(final Collection<MethodInterceptorConfiguration> interceptorConfigurations) {
        this.methodInterceptorConfigurations.addAll(interceptorConfigurations);
    }

    /**
     * The configurations for any component level interceptors for this component type.
     *
     * @return The method interceptor configurations
     */
    public List<MethodInterceptorConfiguration> getComponentInterceptorConfigs() {
        return Collections.unmodifiableList(componentInterceptorConfigurations);
    }

    /**
     * Get the list of view class names.
     *
     * @return the list of view class names
     */
    public List<String> getViewClassNames() {
        return Collections.unmodifiableList(viewClassNames);
    }

    /**
     * Add a view class or interface.
     *
     * @param className the view class name
     */
    public void addViewClassName(final String className) {
        viewClassNames.add(className);
    }

    /**
     * Add a component level interceptor configuration to the component configuration.
     *
     * @param interceptorConfiguration The interceptor configuration
     */
    public void addComponentInterceptorConfig(final MethodInterceptorConfiguration interceptorConfiguration) {
        componentInterceptorConfigurations.add(interceptorConfiguration);
    }

    /**
     * Add component level interceptor configurations to the component configuration.
     *
     * @param interceptorConfigurations The interceptor configurations
     */
    public void addComponentInterceptorConfigs(final MethodInterceptorConfiguration... interceptorConfigurations) {
        for (MethodInterceptorConfiguration config : interceptorConfigurations) {
            addComponentInterceptorConfig(config);
        }
    }

    /**
     * Add component level interceptor configurations to the component configuration.
     *
     * @param interceptorConfigurations The interceptor configurations
     */
    public void addComponentInterceptorConfigs(final Collection<MethodInterceptorConfiguration> interceptorConfigurations) {
        this.componentInterceptorConfigurations.addAll(interceptorConfigurations);
    }

    /**
     * The service name for the naming context this component's environment entries will be bound.
     *
     * @return The environment context service name
     */
    public ServiceName getEnvContextServiceName() {
        return envContextServiceName;
    }

    public void setEnvContextServiceName(ServiceName envContextServiceName) {
        this.envContextServiceName = envContextServiceName;
    }

    public JndiName getBindContextName() {
        return ContextNames.COMPONENT_CONTEXT_NAME;
    }

    @Deprecated
    public void setCompContextServiceName(ServiceName compContextServiceName) {
        addComponentResourceInjection(compContextServiceName, Context.class, AbstractComponent.SET_COMP_CONTEXT);
    }

    @Deprecated
    public void setModuleContextServiceName(ServiceName moduleContextServiceName) {
        addComponentResourceInjection(moduleContextServiceName, Context.class, AbstractComponent.SET_MODULE_CONTEXT);
    }

    @Deprecated
    public void setAppContextServiceName(ServiceName appContextServiceName) {
        addComponentResourceInjection(appContextServiceName, Context.class, AbstractComponent.SET_APP_CONTEXT);
    }

    /**
     * The dependencies generated by this resource injection.
     *
     * @return The dependencies
     */
    public Set<ResourceInjectionDependency<?>> getDependencies() {
        return dependencies;
    }

    public void addDependency(final ResourceInjectionDependency<?> dependency) {
        dependencies.add(dependency);
    }


    public List<ComponentLifecycle> getPostConstructLifecycles() {
        return postConstructLifecycles;
    }

    public void addPostConstructLifecycle(final ComponentLifecycle componentLifecycle) {
        postConstructLifecycles.add(componentLifecycle);
    }

    public List<ComponentLifecycle> getPreDestroyLifecycles() {
        return preDestroyLifecycles;
    }

    public void addPreDestroyLifecycel(final ComponentLifecycle componentLifecycle) {
        preDestroyLifecycles.add(componentLifecycle);
    }

    public ComponentInterceptorFactories getComponentInterceptorFactories() {
        return componentInterceptorFactories;
    }

    /**
     * Get the list of system interceptor factories for component-level interceptors.
     *
     * @return the system interceptor factory list
     */
    public List<InterceptorFactory> getComponentSystemInterceptorFactories() {
        return Collections.unmodifiableList(componentSystemInterceptorFactories);
    }

    protected <T> void addComponentResourceInjection(ServiceName serviceName, Class<T> injectedType, Method setter) {
        ComponentResourceInjectionConfiguration<T> resourceInjectionConfig = new ComponentResourceInjectionConfiguration(serviceName, injectedType);
        addComponentResourceInjectionConfig(resourceInjectionConfig);
        componentResourceInjections.add(new MethodResourceInjection(Values.immediateValue(setter), resourceInjectionConfig.getInjector(), false));
    }

    /**
     * Add a component resource injection.
     * @param resourceInjection the component resource injection
     */
    public void addComponentResourceInjectionConfig(ComponentResourceInjectionConfiguration resourceInjection) {
        componentResourceInjectionConfigs.add(resourceInjection);
    }

    /**
     * Add a system interceptor factory for component-level interceptors.
     *
     * @param factory the interceptor factory
     */
    public void addComponentSystemInterceptorFactory(InterceptorFactory factory) {
        componentSystemInterceptorFactories.add(factory);
    }

    public Iterable<ComponentResourceInjectionConfiguration> getComponentResourceInjectionConfigs() {
        return Collections.unmodifiableList(componentResourceInjectionConfigs);
    }

    public Iterable<ResourceInjection> getComponentResourceInjections() {
        return Collections.unmodifiableList(componentResourceInjections);
    }
}
