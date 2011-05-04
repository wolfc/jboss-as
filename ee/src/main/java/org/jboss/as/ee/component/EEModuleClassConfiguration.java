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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.InjectedValue;

/**
 * Configuration for a class in an EE module.  Each interceptor and component corresponds to one of these.
 * <p/>
 * <h4>Interceptors</h4>
 * The interceptor factories provided herein correspond to interceptors which are defined as part of this class.
 * How the constructed interceptor is used will vary depending on whether this class is being used as a component or
 * interceptor class; in particular, the target of the interceptor context may vary.  In some cases, some or all of
 * the interceptors on this configuration may not be used.
 * <h4>Bindings</h4>
 * The binding configurations contain all the bindings which are created on behalf of this class, regardless of their
 * scope.  When EE module classes are referenced from a component, the bindings are copied to the component, module,
 * app, or global level as appropriate.
 * <h4>MSC Service Dependencies</h4>
 * The listed MSC service dependency injections are not directly used.  It is up to the components consuming this class
 * to make sure that the appropriate dependencies are established for those services; the injections provided by this
 * class may act as a source list.  The MSC service dependencies list DOES NOT include any injected JNDI bindings upon
 * which this EE class depends; these are assembled by the consuming component because different components may have
 * different dependencies (due, for example, to the namespacing of JNDI contexts).
 * <h4>Resource Injections</h4>
 * Instances of this class define the resource injections necessary to construct an instance.  It is up to the consuming
 * service to execute these injections.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EEModuleClassConfiguration {

    // Module
    private final EEModuleConfiguration moduleConfiguration;

    // Module class
    private final Class<?> moduleClass;

    // Interceptors
    private final Deque<InterceptorFactory> postConstructInterceptors = new ArrayDeque<InterceptorFactory>();
    private final Deque<InterceptorFactory> preDestroyInterceptors = new ArrayDeque<InterceptorFactory>();
    private final Deque<InterceptorFactory> aroundInvokeInterceptors = new ArrayDeque<InterceptorFactory>();

    // JNDI bindings
    private final List<BindingConfiguration> bindingConfigurations = new ArrayList<BindingConfiguration>();
    private final List<ResourceInjectionConfiguration> injectionConfigurations = new ArrayList<ResourceInjectionConfiguration>();

    // MSC dependencies
    private final Map<ServiceName, InjectedValue<Object>> dependencyInjections = new HashMap<ServiceName, InjectedValue<Object>>();

    // Instantiation
    private ManagedReferenceFactory instantiator;

    EEModuleClassConfiguration(final Class<?> moduleClass, final EEModuleConfiguration moduleConfiguration) {
        this.moduleClass = moduleClass;
        this.moduleConfiguration = moduleConfiguration;
    }

    /**
     * Get the EE module configuration corresponding to this class configuration.
     *
     * @return the module configuration
     */
    public EEModuleConfiguration getModuleConfiguration() {
        return moduleConfiguration;
    }

    /**
     * Get the module class represented by this configuration.
     *
     * @return the module class
     */
    public Class<?> getModuleClass() {
        return moduleClass;
    }

    /**
     * Get the post-construct interceptor deque.
     *
     * @return the deque
     */
    public Deque<InterceptorFactory> getPostConstructInterceptors() {
        return postConstructInterceptors;
    }

    /**
     * Get the pre-destroy interceptor deque.
     *
     * @return the deque
     */
    public Deque<InterceptorFactory> getPreDestroyInterceptors() {
        return preDestroyInterceptors;
    }

    /**
     * Get the around-invoke interceptor deque.
     *
     * @return the deque
     */
    public Deque<InterceptorFactory> getAroundInvokeInterceptors() {
        return aroundInvokeInterceptors;
    }

    /**
     * Get the binding configurations for this EE module class.
     *
     * @return the binding configurations
     */
    public List<BindingConfiguration> getBindingConfigurations() {
        return bindingConfigurations;
    }

    /**
     * Get the resource injection configurations for this EE module class.
     *
     * @return the resource injection configuration
     */
    public List<ResourceInjectionConfiguration> getInjectionConfigurations() {
        return injectionConfigurations;
    }

    /**
     * Get the dependency injections for this module class.
     *
     * @return the dependency injections
     */
    public Map<ServiceName, InjectedValue<Object>> getDependencyInjections() {
        return dependencyInjections;
    }

    /**
     * Get the class instantiator.
     *
     * @return the class instantiator
     */
    public ManagedReferenceFactory getInstantiator() {
        return instantiator;
    }

    /**
     * Set the class instantiator.
     *
     * @param instantiator the class instantiator
     */
    public void setInstantiator(final ManagedReferenceFactory instantiator) {
        this.instantiator = instantiator;
    }
}
