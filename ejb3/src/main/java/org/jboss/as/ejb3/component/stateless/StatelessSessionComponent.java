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
import org.jboss.as.ee.component.Component;
import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.ComponentInstance;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.ejb3.effigy.common.JBossSessionBeanEffigy;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;

import javax.annotation.Resource;
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
     * @param componentConfiguration
     * @param deploymentClassLoader
     * @param index
     */
    public StatelessSessionComponent(final ComponentConfiguration componentConfiguration, final ClassLoader deploymentClassLoader, final DeploymentReflectionIndex index) {
        this(componentConfiguration, deploymentClassLoader, index, null);
    }

    /**
     * Constructs a StatelessEJBComponent for a stateless session bean
     *
     * @param componentConfiguration
     * @param deploymentClassLoader
     * @param index
     * @param componentInterceptors
     */
    public StatelessSessionComponent(final ComponentConfiguration componentConfiguration, final ClassLoader deploymentClassLoader, final DeploymentReflectionIndex index, List<Interceptor> componentInterceptors) {
        super(componentConfiguration, deploymentClassLoader, index);
        this.componentInterceptors = componentInterceptors;
    }

    @Override
    protected AbstractComponentInstance constructComponentInstance(Object instance) {
        return new StatelessSessionComponentInstance(this, instance);
    }

    // TODO: I need to understand what exactly is this method meant for
    @Override
    public Interceptor createClientInterceptor(Class<?> viewClass) {
        // TODO: Needs to be implemented
        return new Interceptor() {

            @Override
            public Object processInvocation(InterceptorContext context) throws Exception {
                // setup the component being invoked
                context.putPrivateData(Component.class, StatelessSessionComponent.this);
                return context.proceed();
            }
        };
    }

    //TODO: This should be getInstance()
    @Override
    public ComponentInstance createInstance() {
        // TODO: Use a pool
        return super.createInstance();
    }

}
