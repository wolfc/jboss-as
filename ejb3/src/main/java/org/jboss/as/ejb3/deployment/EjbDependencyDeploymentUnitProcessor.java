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

package org.jboss.as.ejb3.deployment;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.EnterpriseBeansMetaData;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;

/**
 * Responsible for adding appropriate Java EE {@link org.jboss.as.server.deployment.module.ModuleDependency module dependencies}
 * <p/>
 * Author : Jaikiran Pai
 */
public class EjbDependencyDeploymentUnitProcessor implements DeploymentUnitProcessor {

    // TODO: This should be centralized some place
    /**
     * Module id for Java EE module
     */
    private static ModuleIdentifier JAVAEE_MODULE_IDENTIFIER = ModuleIdentifier.create("javaee.api");


    /**
     * Adds Java EE module as a dependency to any deployment unit which is a EJB deployment
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     *
     */
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        // get hold of the deployment unit
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        // fetch the EjbJarMetaData
        EjbJarMetaData ejbJarMetaData = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA);
        if (ejbJarMetaData == null) {
            // nothing to do
            return;
        }

        final ModuleLoader moduleLoader = Module.getSystemModuleLoader();
        deploymentUnit.addToAttachmentList(Attachments.MODULE_DEPENDENCIES, new ModuleDependency(moduleLoader, JAVAEE_MODULE_IDENTIFIER, false, false, false));
    }

    @Override
    public void undeploy(DeploymentUnit context) {
    }
}
