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

package org.jboss.as.connector.subsystems.resourceadapters;

import org.jboss.as.connector.ConnectorServices;
import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;

import java.util.List;

import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RESOURCEADAPTER_NAME;

/**
 * Handler for adding the datasource subsystem.
 *
 * @author @author <a href="mailto:stefano.maestri@redhat.com">Stefano
 *         Maestri</a>
 * @author John Bailey
 */
class ResourceAdaptersSubSystemAdd extends AbstractBoottimeAddStepHandler {

    static final ResourceAdaptersSubSystemAdd INSTANCE = new ResourceAdaptersSubSystemAdd();

    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler, List<ServiceController<?>> newControllers) throws OperationFailedException {
        final ResourceAdaptersService.ModifiableResourceAdaptors resourceAdapters = new ResourceAdaptersService.ModifiableResourceAdaptors(null);
        final ServiceTarget serviceTarget = context.getServiceTarget();
        newControllers.add(serviceTarget.addService(ConnectorServices.RESOURCEADAPTERS_SERVICE,
                new ResourceAdaptersService(resourceAdapters)).setInitialMode(ServiceController.Mode.ACTIVE).addListener(verificationHandler).install());
    }

    protected void populateModel(ModelNode operation, ModelNode model) {
        model.setEmptyObject();
        model.get(RESOURCEADAPTER_NAME);
    }
}
