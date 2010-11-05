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

package org.jboss.as.txn;

import javax.transaction.UserTransaction;

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

/**
 * Service responsible for providing the {@link UserTransaction} as OSGi service.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 29-Oct-2010
 */
public class UserTransactionOSGiService implements Service<UserTransactionService> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("osgi", "UserTransaction");

    private InjectedValue<UserTransactionService> injectedUserTransaction = new InjectedValue<UserTransactionService>();
    private InjectedValue<BundleContext> injectedBundleContext = new InjectedValue<BundleContext>();

    public static void addService(final BatchBuilder batchBuilder) {
        UserTransactionOSGiService service = new UserTransactionOSGiService();
        BatchServiceBuilder<?> serviceBuilder = batchBuilder.addService(SERVICE_NAME, service);
        serviceBuilder.addDependency(UserTransactionService.SERVICE_NAME, UserTransactionService.class,
                service.injectedUserTransaction);
        serviceBuilder.addDependency(BundleContextService.SERVICE_NAME, BundleContext.class, service.injectedBundleContext);
        serviceBuilder.setInitialMode(Mode.PASSIVE);
    }

    public synchronized void start(StartContext context) throws StartException {
        BundleContext systemContext = injectedBundleContext.getValue();
        ServiceFactory serviceFactory = new UserTransactionFactory();
        systemContext.registerService(UserTransaction.class.getName(), serviceFactory, null);
    }

    public synchronized void stop(StopContext context) {
    }

    @Override
    public UserTransactionService getValue() throws IllegalStateException {
        return injectedUserTransaction.getValue();
    }

    class UserTransactionFactory implements ServiceFactory {

        @Override
        public Object getService(Bundle bundle, ServiceRegistration sreg) {
            return injectedUserTransaction.getValue().getUserTransaction();
        }

        @Override
        public void ungetService(Bundle bundle, ServiceRegistration sreg, Object service) {
        }
    }
}
