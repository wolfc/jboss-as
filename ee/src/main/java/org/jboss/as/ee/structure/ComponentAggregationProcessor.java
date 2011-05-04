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

package org.jboss.as.ee.structure;

import java.util.List;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEApplicationDescription;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;

import static org.jboss.as.server.deployment.Attachments.SUB_DEPLOYMENTS;
import static org.jboss.as.ee.component.Attachments.EE_MODULE_DESCRIPTION;
import static org.jboss.as.ee.component.Attachments.EE_APPLICATION_DESCRIPTION;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ComponentAggregationProcessor implements DeploymentUnitProcessor {

    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final EEApplicationDescription applicationDescription = new EEApplicationDescription();
        if (deploymentUnit.getAttachment(Attachments.DEPLOYMENT_TYPE) == DeploymentType.EAR) {
            /*
             * We are an EAR, so we must inspect all of our subdeployments and aggregate all their component views
             * into a single index, so that inter-module resolution will work.
             */

            // Add the application description
            final List<DeploymentUnit> subdeployments = deploymentUnit.getAttachmentList(SUB_DEPLOYMENTS);
            for (DeploymentUnit subdeployment : subdeployments) {
                final EEModuleDescription moduleDescription = subdeployment.getAttachment(EE_MODULE_DESCRIPTION);
                if (moduleDescription == null) {
                    // Not an EE deployment.
                    continue;
                }
                for (ComponentDescription componentDescription : moduleDescription.getComponentDescriptions()) {
                    applicationDescription.addComponent(componentDescription);
                }
                subdeployment.putAttachment(EE_APPLICATION_DESCRIPTION, applicationDescription);
            }
        } else if (deploymentUnit.getParent() == null) {
            /*
             * We are a top-level EE deployment, or a non-EE deployment.  Our "aggregate" index is just a copy of
             * our local EE module index.
             */
            final EEModuleDescription moduleDescription = deploymentUnit.getAttachment(EE_MODULE_DESCRIPTION);
            if (moduleDescription == null) {
                // Not an EE deployment.
                return;
            }
            for (ComponentDescription componentDescription : moduleDescription.getComponentDescriptions()) {
                applicationDescription.addComponent(componentDescription);
            }
            deploymentUnit.putAttachment(EE_APPLICATION_DESCRIPTION, applicationDescription);
        }
    }

    public void undeploy(final DeploymentUnit context) {
    }
}
