/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.ee.component.interceptors;

import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.ComponentConfigurator;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.invocation.ImmediateInterceptorFactory;
import org.jboss.invocation.InterceptorFactory;

import java.lang.reflect.Method;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class LifecycleAwareInterceptorComponentConfigurator implements ComponentConfigurator {
    private final LifecycleAwareInterceptor interceptor;

    public LifecycleAwareInterceptorComponentConfigurator(LifecycleAwareInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void configure(DeploymentPhaseContext context, ComponentDescription description, ComponentConfiguration configuration) throws DeploymentUnitProcessingException {
        configuration.getPostConstructInterceptors().add(new ImmediateInterceptorFactory(new PostConstructInterceptorAdapter(interceptor)));
        configuration.getPreDestroyInterceptors().add(new ImmediateInterceptorFactory(new PreDestroyInterceptorAdapter(interceptor)));
        final InterceptorFactory interceptorFactory = new ImmediateInterceptorFactory(interceptor);
        for (final Method method : configuration.getDefinedComponentMethods()) {
            configuration.getComponentInterceptorDeque(method).addFirst(interceptorFactory);
        }
    }
}
