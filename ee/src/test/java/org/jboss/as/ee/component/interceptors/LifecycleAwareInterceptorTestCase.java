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
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEModuleClassConfiguration;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.junit.Test;

import java.lang.reflect.Method;

import static junit.framework.Assert.assertEquals;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class LifecycleAwareInterceptorTestCase {
    @Test
    public void test1() throws DeploymentUnitProcessingException {
        final ComponentDescription description = null;
        final EEModuleClassConfiguration moduleClassConfiguration = null;
        final ComponentConfiguration configuration = new ComponentConfiguration(description, moduleClassConfiguration);

        // mimic two methods
        for (final Method method : TestBean.class.getDeclaredMethods()) {
            configuration.getComponentInterceptorDeque(method);
        }

        final TestInterceptor interceptor = new TestInterceptor();
        final LifecycleAwareInterceptorComponentConfigurator configurator = new LifecycleAwareInterceptorComponentConfigurator(interceptor);
        final DeploymentPhaseContext context = null;
        configurator.configure(context, description, configuration);
        assertEquals(1, configuration.getPostConstructInterceptors().size());
        assertEquals(1, configuration.getPreDestroyInterceptors().size());
        assertEquals(2, configuration.getDefinedComponentMethods().size());
        for (final Method method : configuration.getDefinedComponentMethods()) {
            assertEquals(1, configuration.getComponentInterceptorDeque(method).size());
        }
    }
}
