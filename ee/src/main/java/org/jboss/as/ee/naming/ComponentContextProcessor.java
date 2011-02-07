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

package org.jboss.as.ee.naming;

import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.processor.AbstractComponentConfigProcessor;
import org.jboss.as.naming.service.ContextService;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

import javax.naming.Context;

/**
 * Sets up the service for java:comp context for each of the {@link org.jboss.as.ee.component.Component}s in the
 * deployment unit.
 * <p/>
 * Author: Jaikiran Pai
 */
public class ComponentContextProcessor extends AbstractComponentConfigProcessor {

    @Override
    protected void processComponentConfig(DeploymentUnit deploymentUnit, DeploymentPhaseContext phaseContext, ComponentConfiguration componentConfiguration) throws DeploymentUnitProcessingException {
        // get the name of the component being processed
        String componentName = componentConfiguration.getName();

        ServiceTarget serviceTarget = phaseContext.getServiceTarget();

        // get the service name for this component
        // TODO: The ServiceName generation should perhaps be in a common place like ContextServiceNameBuilder.comp(DeploymentUnit deploymentUnit, String componentName)
        final ServiceName componentServiceName = this.getComponentServiceName(deploymentUnit, componentName);
        final RootContextService contextService = new RootContextService();
        // Add the component's RootContextService
        serviceTarget.addService(componentServiceName, contextService).install();

        // setup java:comp/env service
        ContextService envContextService = new ContextService("env");
        serviceTarget.addService(componentServiceName.append("env"), envContextService)
                .addDependency(componentServiceName, Context.class, envContextService.getParentContextInjector())
                .install();

        // Add this the NamingContextConfig to the list of component context configs for this DU
        deploymentUnit.addToAttachmentList(Attachments.COMP_CONTEXT_CONFIGS, componentServiceName);

        // TODO: Need to install a NamespaceSelector for each component?
//        ServiceName appServiceName = ContextServiceNameBuilder.app(deploymentUnit);
//        ServiceName moduleServiceName = ContextServiceNameBuilder.module(deploymentUnit);
//        // TODO: Needs a unique name per component
//        ServiceName namespaceSelectorServiceName = deploymentUnit.getServiceName().append(NamespaceSelectorService.NAME);
//
//        NamespaceSelectorService namespaceSelector = new NamespaceSelectorService();
//        serviceTarget.addService(namespaceSelectorServiceName, namespaceSelector).addDependency(appServiceName, Context.class,
//                namespaceSelector.getApp()).addDependency(moduleServiceName, Context.class,
//                namespaceSelector.getModule()).addDependency(componentServiceName, Context.class,
//                namespaceSelector.getComp()).install();
    }


    private ServiceName getComponentServiceName(DeploymentUnit deploymentUnit, String componentName) {
        DeploymentUnit parent = deploymentUnit.getParent();
        if (parent == null) {
            return ContextNames.COMPONENT_CONTEXT_SERVICE_NAME.append(deploymentUnit.getName()).append(componentName);
        }
        return ContextNames.COMPONENT_CONTEXT_SERVICE_NAME.append(parent.getName()).append(deploymentUnit.getName()).append(componentName);
    }

    @Override
    protected void undeployComponentConfig(DeploymentUnit deploymentUnit, ComponentConfiguration componentConfiguration) {
        String componentName = componentConfiguration.getName();
        ServiceName componentServiceName = this.getComponentServiceName(deploymentUnit, componentName);
        // Remove the component's service
        final ServiceController<?> componentServiceController = deploymentUnit.getServiceRegistry().getService(componentServiceName);
        if (componentServiceController != null) {
            componentServiceController.setMode(ServiceController.Mode.REMOVE);
        }
    }
}
