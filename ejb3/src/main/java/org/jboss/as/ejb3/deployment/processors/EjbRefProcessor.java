/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.ejb3.deployment.processors;

import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.AbstractDeploymentDescriptorBindingsProcessor;
import org.jboss.as.ee.component.DeploymentDescriptorEnvironment;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.metadata.javaee.spec.EJBLocalReferenceMetaData;
import org.jboss.metadata.javaee.spec.EJBLocalReferencesMetaData;
import org.jboss.msc.service.ServiceName;

import java.util.ArrayList;
import java.util.List;

/**
 * Deployment processor responsible for processing ejb references from deployment descriptors
 *
 * @author Stuart Douglas
 */
public class EjbRefProcessor extends AbstractDeploymentDescriptorBindingsProcessor {

    /**
     * Resolves ejb-ref and ejb-local-ref elements
     *
     * @param deploymentUnit
     * @param environment The environment to resolve the elements for
     * @param classLoader The deployment class loader
     * @param deploymentReflectionIndex The reflection index
     * @return The bindings for the environment entries
     */
     protected List<BindingDescription> processDescriptorEntries(DeploymentUnit deploymentUnit, DeploymentDescriptorEnvironment environment, EEModuleDescription moduleDescription, ComponentDescription componentDescription, ClassLoader classLoader, DeploymentReflectionIndex deploymentReflectionIndex) throws DeploymentUnitProcessingException {
        EJBLocalReferencesMetaData ejbLocalRefs = environment.getEnvironment().getEjbLocalReferences();
        List<BindingDescription> bindingDescriptions = new ArrayList<BindingDescription>();
        //TODO: this needs a lot more work
        if(ejbLocalRefs != null) {
            for(EJBLocalReferenceMetaData ejbRef : ejbLocalRefs) {
                String name = ejbRef.getEjbRefName();
                String ejbName = ejbRef.getLink();
                String lookup = ejbRef.getLookupName();
                String localInterface = ejbRef.getLocal();
                Class<?> localInterfaceType = null;

                if(!isEmpty(localInterface)) {
                    try {
                        classLoader.loadClass(localInterface);
                    } catch (ClassNotFoundException e) {
                        throw new DeploymentUnitProcessingException("Could not load local interface type " + localInterface,e);
                    }
                }

                if(!name.startsWith("java:")) {
                    name = environment.getDefaultContext() + name;
                }

                BindingDescription bindingDescription = new BindingDescription(name);
                bindingDescriptions.add(bindingDescription);


                //add any injection targets
                localInterfaceType = processInjectionTargets(classLoader,deploymentReflectionIndex,ejbRef,bindingDescription,localInterfaceType);

                if(localInterfaceType == null) {
                    throw new DeploymentUnitProcessingException("Could not determine type of ejb-local-ref " + name + " for component " + componentDescription);
                }
                bindingDescription.setBindingType(localInterfaceType.getName());

                if (!isEmpty(lookup)) {
                    if(componentDescription != null ) {
                        bindingDescription.setReferenceSourceDescription(new LookupBindingSourceDescription(lookup,componentDescription));
                    } else {
                        bindingDescription.setReferenceSourceDescription(new LookupBindingSourceDescription(lookup,moduleDescription));
                    }
                } else if (!isEmpty(ejbName)) {
                    //TODO: implement cross deployment references
                    final ServiceName beanServiceName = deploymentUnit.getServiceName()
                        .append("component").append(ejbName).append("VIEW").append(bindingDescription.getBindingType());
                    bindingDescription.setReferenceSourceDescription(new ServiceBindingSourceDescription(beanServiceName));
                } else {
                    bindingDescription.setReferenceSourceDescription(new LazyBindingSourceDescription());
                }
            }
        }
        return bindingDescriptions;
    }

    private boolean isEmpty(String string) {
        return string == null || string.isEmpty();
    }

}
