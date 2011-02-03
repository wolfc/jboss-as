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

package org.jboss.as.ejb3.component.stateless;

import org.jboss.as.ee.component.AbstractComponent;
import org.jboss.as.ee.component.AbstractComponentInstance;
import org.jboss.as.ee.component.ComponentInstance;
import org.jboss.as.ee.component.injection.ResourceInjection;
import org.jboss.as.ee.component.interceptor.ComponentInterceptorFactories;
import org.jboss.as.ee.component.lifecycle.ComponentLifecycle;
import org.jboss.ejb3.effigy.common.JBossSessionBeanEffigy;
import org.jboss.invocation.Interceptor;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * {@link org.jboss.as.ee.component.Component} responsible for managing EJB3 stateless session beans
 * <p/>
 * <p/>
 * Author : Jaikiran Pai
 */
public class StatelessSessionComponent extends AbstractComponent {


    // TODO: Need to use the right "name" for the @Resource
    @Resource
    private JBossSessionBeanEffigy sessionBeanEffigy;

    /**
     * The component level interceptors that will be applied during the invocation
     * on the bean
     */
    private List<Interceptor> componentInterceptors;

    // some more injectable resources
    // @Resource
    // private Pool pool;

    /**
     * Constructs a StatelessEJBComponent for a stateless session bean
     *
     * @param beanClass                  The SLSB bean class
     * @param beanClassLoader            The classloader
     * @param resourceInjections         ResourceInjection(s) for the bean
     * @param postConstrucInterceptors   Post-construct callbacks for the SLSB
     * @param preDestroyInterceptors     The pre-destroy callbacks for the SLSB
     * @param methodInterceptorFactories
     */
    public StatelessSessionComponent(final Class<?> beanClass, final ClassLoader beanClassLoader, final List<ResourceInjection> resourceInjections, final List<ComponentLifecycle> postConstrucInterceptors, final List<ComponentLifecycle> preDestroyInterceptors, final ComponentInterceptorFactories methodInterceptorFactories) {
        this(beanClass, beanClassLoader, resourceInjections, postConstrucInterceptors, preDestroyInterceptors, methodInterceptorFactories, null);
    }

    /**
     * Constructs a StatelessEJBComponent for a stateless session bean
     *
     * @param beanClass                  The SLSB bean class
     * @param beanClassLoader            The classloader
     * @param resourceInjections         ResourceInjection(s) for the bean
     * @param postConstrucInterceptors   Post-construct callbacks for the SLSB
     * @param preDestroyInterceptors     The pre-destroy callbacks for the SLSB
     * @param methodInterceptorFactories
     * @param componentInterceptors      The component interceptors
     */
    public StatelessSessionComponent(final Class<?> beanClass, final ClassLoader beanClassLoader, final List<ResourceInjection> resourceInjections, final List<ComponentLifecycle> postConstrucInterceptors, final List<ComponentLifecycle> preDestroyInterceptors, final ComponentInterceptorFactories methodInterceptorFactories, List<Interceptor> componentInterceptors) {
        super(beanClass, beanClassLoader, resourceInjections, postConstrucInterceptors, preDestroyInterceptors, methodInterceptorFactories);
        this.componentInterceptors = componentInterceptors;
    }

    @Override
    protected AbstractComponentInstance createComponentInstance(Object instance) {
        return new StatelessSessionComponentInstance(this, null, instance);
    }

    @Override
    public ComponentInstance getInstance() {
        // TODO: Use a pool
        return super.getInstance();
    }

    @Override
    protected List<Interceptor> getComponentLevelInterceptors() {
        List<Interceptor> interceptors = super.getComponentLevelInterceptors();
        if (interceptors == null || interceptors.isEmpty()) {
            return Collections.unmodifiableList(this.componentInterceptors);
        }
        interceptors.addAll(this.componentInterceptors);
        return interceptors;
    }

}
