/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.web.deployment.component;

import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.ComponentInstance;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

import java.util.Collections;
import java.util.Set;

/**
 * Instantiator for normal ee web components
 *
 * @author Stuart Douglas
 */
public class WebComponentInstantiator implements ComponentInstantiator {

    private volatile WebComponent component;
    private final ServiceRegistry serviceRegistry;
    private final ServiceName serviceName;

    public WebComponentInstantiator(DeploymentUnit deploymentUnit, ComponentDescription componentDescription) {
        serviceName = deploymentUnit.getServiceName().append("component").append(componentDescription.getComponentName()).append("START");
        this.serviceRegistry = deploymentUnit.getServiceRegistry();
    }

    @Override
    public ManagedReference getReference() {
        if (component == null) {
            synchronized (this) {
                if (component == null) {
                    component = ((ServiceController<WebComponent>) serviceRegistry.getRequiredService(serviceName)).getValue();
                }
            }
        }
        return new ManagedReference() {

            private final ComponentInstance instance = component.createInstance();
            private boolean destroyed;

            @Override
            public synchronized void release() {
                if (!destroyed) {
                    // TODO: Implement destroyInstance
//                    instance.getComponent().destroyInstance(instance);
                    destroyed = true;
                    throw new RuntimeException("destroyInstance for web components not yet implemented");
                }
            }

            @Override
            public Object getInstance() {
                return instance.getInstance();
            }
        };
    }


    @Override
    public Set<ServiceName> getServiceNames() {
        return Collections.singleton(serviceName);
    }


}
