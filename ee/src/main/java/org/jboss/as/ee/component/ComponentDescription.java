/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.invocation.ImmediateInterceptorFactory;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.Interceptors;
import org.jboss.invocation.proxy.MethodIdentifier;
import org.jboss.invocation.proxy.ProxyFactory;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A description of a generic Java EE component.  The description is pre-classloading so it references everything by name.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class ComponentDescription {

    private static final DefaultFirstConfigurator FIRST_CONFIGURATOR = new DefaultFirstConfigurator();

    private static final AtomicInteger PROXY_ID = new AtomicInteger(0);

    private final ServiceName serviceName;
    private final String componentName;
    private final String componentClassName;
    private final EEModuleDescription moduleDescription;
    private final EEModuleClassDescription classDescription;

    private List<InterceptorDescription> classInterceptors = new ArrayList<InterceptorDescription>();
    private List<InterceptorDescription> defaultInterceptors = new ArrayList<InterceptorDescription>();

    private final Map<MethodIdentifier, List<InterceptorDescription>> methodInterceptors = new HashMap<MethodIdentifier, List<InterceptorDescription>>();
    private final Map<MethodIdentifier, Set<String>> methodInterceptorsSet = new HashMap<MethodIdentifier, Set<String>>();

    private final Set<MethodIdentifier> methodExcludeDefaultInterceptors = new HashSet<MethodIdentifier>();
    private final Set<MethodIdentifier> methodExcludeClassInterceptors = new HashSet<MethodIdentifier>();

    private final Map<ServiceName, ServiceBuilder.DependencyType> dependencies = new HashMap<ServiceName, ServiceBuilder.DependencyType>();

    private Set<InterceptorDescription> allInterceptors;

    private ComponentNamingMode namingMode = ComponentNamingMode.USE_MODULE;
    private boolean excludeDefaultInterceptors = false;
    private DeploymentDescriptorEnvironment deploymentDescriptorEnvironment;

    private final List<ViewDescription> views = new ArrayList<ViewDescription>();

    // Bindings
    private final List<BindingConfiguration> bindingConfigurations = new ArrayList<BindingConfiguration>();

    private final Queue<ComponentConfigurator> configurators = new ArrayDeque<ComponentConfigurator>();

    /**
     * Construct a new instance.
     *
     * @param componentName             the component name
     * @param componentClassName        the component instance class name
     * @param moduleDescription         the EE module description
     * @param classDescription          the component class' description
     * @param deploymentUnitServiceName the service name of the DU containing this component
     */
    public ComponentDescription(final String componentName, final String componentClassName, final EEModuleDescription moduleDescription, final EEModuleClassDescription classDescription, final ServiceName deploymentUnitServiceName) {
        this.moduleDescription = moduleDescription;
        this.classDescription = classDescription;
        if (componentName == null) {
            throw new IllegalArgumentException("name is null");
        }
        if (componentClassName == null) {
            throw new IllegalArgumentException("className is null");
        }
        if (moduleDescription == null) {
            throw new IllegalArgumentException("moduleName is null");
        }
        if (classDescription == null) {
            throw new IllegalArgumentException("classDescription is null");
        }
        if (deploymentUnitServiceName == null) {
            throw new IllegalArgumentException("deploymentUnitServiceName is null");
        }
        serviceName = deploymentUnitServiceName.append("component").append(componentName);
        this.componentName = componentName;
        this.componentClassName = componentClassName;
        configurators.add(FIRST_CONFIGURATOR);
    }

    public ComponentConfiguration createConfiguration(EEModuleConfiguration moduleConfiguration) {
        return new ComponentConfiguration(this, moduleConfiguration.getClassConfiguration(this.getComponentClassName()));
    }

    /**
     * Get the component name.
     *
     * @return the component name
     */
    public String getComponentName() {
        return componentName;
    }

    /**
     * Get the base service name for this component.
     *
     * @return the base service name
     */
    public ServiceName getServiceName() {
        return serviceName;
    }

    /**
     * Get the component instance class name.
     *
     * @return the component class name
     */
    public String getComponentClassName() {
        return componentClassName;
    }

    /**
     * Get the component's module name.
     *
     * @return the module name
     */
    public String getModuleName() {
        return moduleDescription.getModuleName();
    }

    /**
     * Get the component's module's application name.
     *
     * @return the application name
     */
    public String getApplicationName() {
        return moduleDescription.getApplicationName();
    }

    /**
     * Get the list of interceptor classes applied directly to class. These interceptors will have lifecycle methods invoked
     *
     * @return the interceptor classes
     */
    public List<InterceptorDescription> getClassInterceptors() {
        return classInterceptors;
    }

    /**
     * Override the class interceptors with a new set of interceptors
     *
     * @param classInterceptors
     */
    public void setClassInterceptors(List<InterceptorDescription> classInterceptors) {
        for (InterceptorDescription clazz : classInterceptors) {
            moduleDescription.getOrAddClassByName(clazz.getInterceptorClassName());
        }
        this.classInterceptors = classInterceptors;
        this.allInterceptors = null;
    }


    /**
     * @return the components default interceptors
     */
    public List<InterceptorDescription> getDefaultInterceptors() {
        return defaultInterceptors;
    }

    public void setDefaultInterceptors(final List<InterceptorDescription> defaultInterceptors) {
        allInterceptors = null;
        this.defaultInterceptors = defaultInterceptors;
    }

    /**
     * Returns a combined map of class and method level interceptors
     *
     * @return all interceptors on the class
     */
    public Set<InterceptorDescription> getAllInterceptors() {
        if (allInterceptors == null) {
            allInterceptors = new HashSet<InterceptorDescription>();
            allInterceptors.addAll(classInterceptors);
            if (!excludeDefaultInterceptors) {
                allInterceptors.addAll(defaultInterceptors);
            }
            for (List<InterceptorDescription> interceptors : methodInterceptors.values()) {
                allInterceptors.addAll(interceptors);
            }
        }
        return allInterceptors;
    }

    /**
     * @return <code>true</code> if the <code>ExcludeDefaultInterceptors</code> annotation was applied to the class
     */
    public boolean isExcludeDefaultInterceptors() {
        return excludeDefaultInterceptors;
    }

    public void setExcludeDefaultInterceptors(boolean excludeDefaultInterceptors) {
        allInterceptors = null;
        this.excludeDefaultInterceptors = excludeDefaultInterceptors;
    }

    /**
     * @param method The method that has been annotated <code>@ExcludeDefaultInterceptors</code>
     */
    public void excludeDefaultInterceptors(MethodIdentifier method) {
        methodExcludeDefaultInterceptors.add(method);
    }

    public boolean isExcludeDefaultInterceptors(MethodIdentifier method) {
        return methodExcludeDefaultInterceptors.contains(method);
    }

    /**
     * @param method The method that has been annotated <code>@ExcludeClassInterceptors</code>
     */
    public void excludeClassInterceptors(MethodIdentifier method) {
        methodExcludeClassInterceptors.add(method);
    }

    public boolean isExcludeClassInterceptors(MethodIdentifier method) {
        return methodExcludeClassInterceptors.contains(method);
    }

    /**
     * Add a class level interceptor.
     *
     * @param description the interceptor class description
     * @return {@code true} if the class interceptor was not already defined, {@code false} if it was
     */
    public boolean addClassInterceptor(InterceptorDescription description) {
        String name = description.getInterceptorClassName();
        // add the interceptor class to the EEModuleDescription
        this.moduleDescription.getOrAddClassByName(name);
        if (classInterceptors.contains(description)) {
            return false;
        }
        classInterceptors.add(description);
        this.allInterceptors = null;
        return true;
    }

    /**
     * Returns the {@link InterceptorDescription} for the passed <code>interceptorClassName</code>, if such a class
     * interceptor exists for this component description. Else returns null.
     *
     * @param interceptorClassName The fully qualified interceptor class name
     * @return
     */
    public InterceptorDescription getClassInterceptor(String interceptorClassName) {
        for (InterceptorDescription interceptor : classInterceptors) {
            if (interceptor.getInterceptorClassName().equals(interceptorClassName)) {
                return interceptor;
            }
        }
        return null;
    }

    /**
     * Get the method interceptor configurations.  The key is the method identifier, the value is
     * the set of class names of interceptors to configure on that method.
     *
     * @return the method interceptor configurations
     */
    public Map<MethodIdentifier, List<InterceptorDescription>> getMethodInterceptors() {
        return methodInterceptors;
    }

    /**
     * Add a method interceptor class name.
     *
     * @param method      the method
     * @param description the interceptor descriptor
     * @return {@code true} if the interceptor class was not already associated with the method, {@code false} if it was
     */
    public boolean addMethodInterceptor(MethodIdentifier method, InterceptorDescription description) {
        //we do not add method level interceptors to the set of interceptor classes,
        //as their around invoke annotations
        List<InterceptorDescription> interceptors = methodInterceptors.get(method);
        Set<String> interceptorClasses = methodInterceptorsSet.get(method);
        if (interceptors == null) {
            methodInterceptors.put(method, interceptors = new ArrayList<InterceptorDescription>());
            methodInterceptorsSet.put(method, interceptorClasses = new HashSet<String>());
        }
        final String name = description.getInterceptorClassName();
        // add the interceptor class to the EEModuleDescription
        this.moduleDescription.getOrAddClassByName(name);
        if (interceptorClasses.contains(name)) {
            return false;
        }
        interceptors.add(description);
        interceptorClasses.add(name);
        this.allInterceptors = null;
        return true;
    }

    /**
     * Sets the method level interceptors for a method, and marks it as exclude class and default level interceptors.
     * <p/>
     * This is used to set the final interceptor order after it has been modifier by the deployment descriptor
     *
     * @param identifier
     * @param interceptorDescriptions
     */
    public void setMethodInterceptors(MethodIdentifier identifier, List<InterceptorDescription> interceptorDescriptions) {
        methodInterceptors.put(identifier, interceptorDescriptions);
        methodExcludeClassInterceptors.add(identifier);
        methodExcludeDefaultInterceptors.add(identifier);
    }

    /**
     * Get the naming mode of this component.
     *
     * @return the naming mode
     */
    public ComponentNamingMode getNamingMode() {
        return namingMode;
    }

    /**
     * Set the naming mode of this component.  May not be {@code null}.
     *
     * @param namingMode the naming mode
     */
    public void setNamingMode(final ComponentNamingMode namingMode) {
        if (namingMode == null) {
            throw new IllegalArgumentException("namingMode is null");
        }
        this.namingMode = namingMode;
    }

    public EEModuleDescription getModuleDescription() {
        return moduleDescription;
    }

    public EEModuleClassDescription getClassDescription() {
        return classDescription;
    }

    /**
     * Add a dependency to this component.  If the same dependency is added multiple times, only the first will
     * take effect.
     *
     * @param serviceName the service name of the dependency
     * @param type        the type of the dependency (required or optional)
     */
    public void addDependency(ServiceName serviceName, ServiceBuilder.DependencyType type) {
        if (serviceName == null) {
            throw new IllegalArgumentException("serviceName is null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        final Map<ServiceName, ServiceBuilder.DependencyType> dependencies = this.dependencies;
        final ServiceBuilder.DependencyType dependencyType = dependencies.get(serviceName);
        if (dependencyType == ServiceBuilder.DependencyType.REQUIRED) {
            dependencies.put(serviceName, ServiceBuilder.DependencyType.REQUIRED);
        } else {
            dependencies.put(serviceName, type);
        }
    }

    /**
     * Get the dependency map.
     *
     * @return the dependency map
     */
    public Map<ServiceName, ServiceBuilder.DependencyType> getDependencies() {
        return dependencies;
    }

    public DeploymentDescriptorEnvironment getDeploymentDescriptorEnvironment() {
        return deploymentDescriptorEnvironment;
    }

    public void setDeploymentDescriptorEnvironment(DeploymentDescriptorEnvironment deploymentDescriptorEnvironment) {
        this.deploymentDescriptorEnvironment = deploymentDescriptorEnvironment;
    }


    /**
     * Get the binding configurations for this component.  This list contains bindings which are specific to the
     * component.
     *
     * @return the binding configurations
     */
    public List<BindingConfiguration> getBindingConfigurations() {
        return bindingConfigurations;
    }

    /**
     * Get the list of views which apply to this component.
     *
     * @return the list of views
     */
    public List<ViewDescription> getViews() {
        return views;
    }

    /**
     * Get the configurators for this component.
     *
     * @return the configurators
     */
    public Queue<ComponentConfigurator> getConfigurators() {
        return configurators;
    }

    private static class DefaultFirstConfigurator implements ComponentConfigurator {

        public void configure(final DeploymentPhaseContext context, final ComponentDescription description, final ComponentConfiguration configuration) throws DeploymentUnitProcessingException {
            final DeploymentUnit deploymentUnit = context.getDeploymentUnit();
            final Module module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);

            final InterceptorFactory tcclInterceptor = new ImmediateInterceptorFactory(new TCCLInterceptor(module.getClassLoader()));
            configuration.getPostConstructInterceptors().add(tcclInterceptor);
            configuration.getPreDestroyInterceptors().add(tcclInterceptor);

            //now add the interceptor that initializes and the interceptor that actually invokes to the end of the interceptor chain
            // and also add the tccl interceptor
            for (Method method : configuration.getDefinedComponentMethods()) {
                // add to the beginning
                configuration.getComponentInterceptorDeque(method).add(tcclInterceptor);
                configuration.getComponentInterceptorDeque(method).add(Interceptors.getInitialInterceptorFactory());
            }

            //views
            for (ViewDescription view : description.getViews()) {
                Class<?> viewClass;
                try {
                    viewClass = module.getClassLoader().loadClass(view.getViewClassName());
                } catch (ClassNotFoundException e) {
                    throw new DeploymentUnitProcessingException("Could not load view class " + view.getViewClassName() + " for component " + configuration, e);
                }
                final ViewConfiguration viewConfiguration;
                if (viewClass.isInterface()) {
                    viewConfiguration = new ViewConfiguration(viewClass, configuration, view.getServiceName(), new ProxyFactory(viewClass.getName() + "$$$view" + PROXY_ID.incrementAndGet(), Object.class, viewClass.getClassLoader(), viewClass));
                } else {
                    viewConfiguration = new ViewConfiguration(viewClass, configuration, view.getServiceName(), new ProxyFactory(viewClass.getName() + "$$$view" + PROXY_ID.incrementAndGet(), viewClass, viewClass.getClassLoader()));
                }
                for (final ViewConfigurator configurator : view.getConfigurators()) {
                    configurator.configure(context, configuration, view, viewConfiguration);
                }
                configuration.getViews().add(viewConfiguration);
            }
        }
    }
}
