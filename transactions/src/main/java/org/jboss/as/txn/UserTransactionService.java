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

import org.jboss.as.util.SetContextLoaderAction;
import org.jboss.msc.service.BatchBuilder;
import org.jboss.msc.service.BatchServiceBuilder;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Service responsible for getting the {@link UserTransaction}.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 29-Oct-2010
 */
public class UserTransactionService implements Service<UserTransactionService> {
    public static final ServiceName SERVICE_NAME = TxnServices.JBOSS_TXN_USER_TRANSACTION;

    private InjectedValue<com.arjuna.ats.jbossatx.jta.TransactionManagerService> injectedArjunaTM = new InjectedValue<com.arjuna.ats.jbossatx.jta.TransactionManagerService>();

    public static void addService(final BatchBuilder batchBuilder) {
        UserTransactionService service = new UserTransactionService();
        BatchServiceBuilder<?> serviceBuilder = batchBuilder.addService(SERVICE_NAME, service);
        serviceBuilder.addDependency(ArjunaTransactionManagerService.SERVICE_NAME, com.arjuna.ats.jbossatx.jta.TransactionManagerService.class, service.injectedArjunaTM);
    }

    public synchronized void start(StartContext context) throws StartException {
    }

    public synchronized void stop(StopContext context) {
    }

    public UserTransaction getUserTransaction() throws IllegalStateException {
        ClassLoader tccl = SetContextLoaderAction.setContextLoader(getClass().getClassLoader());
        try {
            return injectedArjunaTM.getValue().getUserTransaction();
        } finally {
            SetContextLoaderAction.setContextLoader(tccl);
        }
    }

    @Override
    public UserTransactionService getValue() throws IllegalStateException {
        return this;
    }
}
