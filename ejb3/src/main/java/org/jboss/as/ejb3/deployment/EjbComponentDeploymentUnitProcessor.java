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

package org.jboss.as.ejb3.deployment;

import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ejb3.component.StatelessEJBComponent;
import org.jboss.as.managedbean.component.ManagedBeanComponentFactory;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.ejb3.effigy.common.JBossBeanEffigyInfo;
import org.jboss.ejb3.effigy.common.JBossSessionBeanEffigy;
import org.jboss.ejb3.effigy.int2.JBossBeanEffigyFactory;
import org.jboss.logging.Logger;
import org.jboss.metadata.ejb.jboss.JBoss51MetaData;
import org.jboss.metadata.ejb.jboss.JBossEnterpriseBeanMetaData;
import org.jboss.metadata.ejb.jboss.JBossEnterpriseBeansMetaData;
import org.jboss.metadata.ejb.jboss.JBossSessionBeanMetaData;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.EnterpriseBeansMetaData;
import org.jboss.modules.Module;

/**
 * Deployment unit processor which will pick up {@link EjbJarMetaData} attachment(s) from deployment unit
 * and create appropriate {@link org.jboss.as.ee.component.ComponentConfiguration}(s) for each enterprise bean
 * within the {@link org.jboss.metadata.ejb.spec.EjbJarMetaData}.
 * <p/>
 * EJB containers are deployed as Java EE Managed Beans (JSR-316). As such the {@link ComponentConfiguration} created
 * in this deployer will be corresponding to managed bean configurations whose bean class is an appropriate EJB component (for
 * example, {@link StatelessEJBComponent})
 * <p/>
 * Author: Jaikiran Pai
 */
public class EjbComponentDeploymentUnitProcessor implements DeploymentUnitProcessor {

    /**
     * Logger
     */
    private static Logger logger = Logger.getLogger(EjbComponentDeploymentUnitProcessor.class);

    /**
     *
     */
    // TODO: Find a better place for this
    private static final String EJB_COMPONENT_PREFIX = "org.jboss.ejb3:";

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

        EnterpriseBeansMetaData ejbs = ejbJarMetaData.getEnterpriseBeans();
        if (ejbs == null || ejbs.isEmpty()) {
            // no beans!
            return;
        }

        JBoss51MetaData jbossMetaData = new JBoss51MetaData();
        jbossMetaData.merge(null, ejbJarMetaData);

        JBossEnterpriseBeansMetaData jejbs = jbossMetaData.getEnterpriseBeans();
        for (JBossEnterpriseBeanMetaData ejb : jejbs) {
            if (!ejb.isSession()) {
                logger.warn("Only stateless session EJBs are supported currently. Skipping " + ejb.getName() + " from deployment unit: " + deploymentUnit);
                continue;
            }
            JBossSessionBeanMetaData sessionBean = (JBossSessionBeanMetaData) ejb;
            if (!sessionBean.isStateless()) {
                logger.warn("Only stateless session EJBs are supported currently. Skipping " + ejb.getName() + " from deployment unit: " + deploymentUnit);
                continue;
            }
            // attach a component config for stateless EJB component to deploy it as a Managed Bean
            this.createAndAttachManagedBeanComponentConfig(deploymentUnit, sessionBean);

            // TODO: Once we have jboss.xml processing, this won't be needed and we'll solely work on JBossMetaData
            // and JBoss*BeanMetaData
            // now create Effigy for this EJB and make it available in JNDI so that it can be injected
            // into the StatelessEJBComponent (a.k.a container)
            JBossSessionBeanEffigy sessionBeanEffigy = this.getJBossSessionBeanEffigy(this.getClassLoader(deploymentUnit), sessionBean);
            // TODO: Bind it to jndi.
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    private void createAndAttachManagedBeanComponentConfig(DeploymentUnit deploymentUnit, JBossSessionBeanMetaData sessionBean) {
        // TODO: This isn't foolproof yet. Need a better naming
        String ejbComponentName = EJB_COMPONENT_PREFIX + deploymentUnit.getName() + sessionBean.getName();
        ComponentConfiguration sessionBeanComponentConfig = new ComponentConfiguration(ejbComponentName, StatelessEJBComponent.class.getName(), ManagedBeanComponentFactory.INSTANCE);

        // add this component configuration as an attachment to the deployment unit
        deploymentUnit.addToAttachmentList(org.jboss.as.ee.component.Attachments.COMPONENT_CONFIGS, sessionBeanComponentConfig);
    }

    private JBossSessionBeanEffigy getJBossSessionBeanEffigy(ClassLoader cl, JBossSessionBeanMetaData sessionBean) throws DeploymentUnitProcessingException {
        JBossBeanEffigyFactory effigyFactory = new JBossBeanEffigyFactory();
        JBossBeanEffigyInfo effigyInfo = new JBossBeanEffigyInfo(cl, sessionBean);
        try {
            return effigyFactory.create(effigyInfo, JBossSessionBeanEffigy.class);
        } catch (ClassNotFoundException cnfe) // hmm, why does it have a CNFE in throws clause?
        {
            throw new DeploymentUnitProcessingException(cnfe);
        }
    }

    private ClassLoader getClassLoader(DeploymentUnit deploymentUnit) {
        Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        if (module == null) {
            throw new IllegalStateException("Module not found for deployment unit: " + deploymentUnit);
        }
        return module.getClassLoader();
    }
}
