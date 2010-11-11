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

package org.jboss.as.naming.osgi;

import org.jboss.as.naming.NamingStore;
import org.jboss.as.naming.service.NamingService;
import org.jboss.as.osgi.service.BundleContextService;
import org.jboss.msc.service.BatchBuilder;
import org.jboss.msc.service.BatchServiceBuilder;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Service responsible for providing the {@link InitialContext} as OSGi service.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 29-Oct-2010
 */
public class InitialContextOSGiService implements Service<Void> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("osgi", "InitialContext");

    private InjectedValue<NamingStore> injectedNamingStore = new InjectedValue<NamingStore>();
    private InjectedValue<BundleContext> injectedBundleContext = new InjectedValue<BundleContext>();

    public static void addService(final BatchBuilder batchBuilder) {
        InitialContextOSGiService service = new InitialContextOSGiService();
        BatchServiceBuilder<?> serviceBuilder = batchBuilder.addService(SERVICE_NAME, service);
        serviceBuilder.addDependency(NamingService.SERVICE_NAME, NamingStore.class, service.injectedNamingStore);
        serviceBuilder.addDependency(BundleContextService.SERVICE_NAME, BundleContext.class, service.injectedBundleContext);
        serviceBuilder.setInitialMode(Mode.PASSIVE);
    }

    public synchronized void start(StartContext context) throws StartException {
        BundleContext systemContext = injectedBundleContext.getValue();
        ServiceFactory serviceFactory = new InitialContextServiceFactory();
        systemContext.registerService(InitialContext.class.getName(), serviceFactory, null);
    }

    public synchronized void stop(StopContext context) {
    }

    @Override
    public Void getValue() throws IllegalStateException {
        return null;
    }

    class InitialContextServiceFactory implements ServiceFactory {

        @Override
        public Object getService(Bundle bundle, ServiceRegistration sreg) {
            try {
                return new InitialContext();
            } catch (NamingException ex) {
                throw new IllegalStateException("Cannot obtain InitialContext", ex);
            }
        }

        @Override
        public void ungetService(Bundle bundle, ServiceRegistration sreg, Object service) {
        }
    }
}
