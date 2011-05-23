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
package org.jboss.as.ee.component;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndex;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndexUtil;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.invocation.ImmediateInterceptorFactory;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.Interceptors;

import java.lang.reflect.Method;

import static org.jboss.as.server.deployment.Attachments.REFLECTION_INDEX;

/**
 * Dispatch to the proper component method.
 *
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class DispatchViewConfigurator extends AbstractViewConfiguratorProcessor {
    private static class ComponentDispatcherInterceptor implements Interceptor {

        private final Method componentMethod;

        public ComponentDispatcherInterceptor(final Method componentMethod) {
            this.componentMethod = componentMethod;
        }

        public Object processInvocation(final InterceptorContext context) throws Exception {
            ComponentInstance componentInstance = context.getPrivateData(ComponentInstance.class);
            if (componentInstance == null) {
                throw new IllegalStateException("No component instance associated");
            }
            Method oldMethod = context.getMethod();
            try {
                context.setMethod(componentMethod);
                context.setTarget(componentInstance.getInstance());
                return componentInstance.getInterceptor(componentMethod).processInvocation(context);
            } finally {
                context.setMethod(oldMethod);
                context.setTarget(null);
            }
        }
    }

    private static final ImmediateInterceptorFactory CLIENT_DISPATCHER_INTERCEPTOR_FACTORY = new ImmediateInterceptorFactory(new Interceptor() {
        public Object processInvocation(final InterceptorContext context) throws Exception {
            ComponentViewInstance viewInstance = context.getPrivateData(ComponentViewInstance.class);
            return viewInstance.getEntryPoint(context.getMethod()).processInvocation(context);
        }
    });

    @Override
    public void configure(DeploymentPhaseContext context, ComponentConfiguration componentConfiguration, ViewDescription description, ViewConfiguration configuration) throws DeploymentUnitProcessingException {
        // Create method indexes
        DeploymentReflectionIndex reflectionIndex = context.getDeploymentUnit().getAttachment(REFLECTION_INDEX);
        ClassReflectionIndex<?> index = reflectionIndex.getClassIndex(componentConfiguration.getComponentClass());
        Method[] methods = configuration.getProxyFactory().getCachedMethods();
        for (Method method : methods) {
            final Method componentMethod = ClassReflectionIndexUtil.findRequiredMethod(reflectionIndex, index, method);
            configuration.getViewInterceptorDeque(method).add(new ImmediateInterceptorFactory(new ComponentDispatcherInterceptor(componentMethod)));
            configuration.getClientInterceptorDeque(method).add(CLIENT_DISPATCHER_INTERCEPTOR_FACTORY);
        }

        configuration.getViewPostConstructInterceptors().add(Interceptors.getTerminalInterceptorFactory());
        configuration.getViewPreDestroyInterceptors().add(Interceptors.getTerminalInterceptorFactory());

        configuration.getClientPostConstructInterceptors().add(Interceptors.getTerminalInterceptorFactory());
        configuration.getClientPreDestroyInterceptors().add(Interceptors.getTerminalInterceptorFactory());
    }
}
