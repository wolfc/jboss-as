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

import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.interceptor.MethodInterceptorAllFilter;
import org.jboss.as.ee.component.interceptor.MethodInterceptorConfiguration;
import org.jboss.as.ee.component.interceptor.MethodInterceptorFilter;
import org.jboss.as.ee.naming.ContextNames;
import org.jboss.as.ee.naming.ContextServiceNameBuilder;
import org.jboss.as.ejb3.component.EJBComponentConfiguration;
import org.jboss.as.ejb3.component.stateful.StatefulSessionComponentConfiguration;
import org.jboss.as.ejb3.component.stateless.DummyComponentInterceptor;
import org.jboss.as.ejb3.component.stateless.StatelessSessionComponentFactory;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.invocation.ImmediateInterceptorFactory;
import org.jboss.logging.Logger;
import org.jboss.metadata.ejb.jboss.JBoss51MetaData;
import org.jboss.metadata.ejb.jboss.JBossAssemblyDescriptorMetaData;
import org.jboss.metadata.ejb.jboss.JBossEnterpriseBeanMetaData;
import org.jboss.metadata.ejb.jboss.JBossEnterpriseBeansMetaData;
import org.jboss.metadata.ejb.jboss.JBossMetaData;
import org.jboss.metadata.ejb.jboss.JBossSessionBean31MetaData;
import org.jboss.metadata.ejb.jboss.JBossSessionBeanMetaData;
import org.jboss.metadata.ejb.spec.AroundInvokeMetaData;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.EnterpriseBeansMetaData;
import org.jboss.metadata.ejb.spec.InterceptorBindingMetaData;
import org.jboss.metadata.ejb.spec.InterceptorsMetaData;
import org.jboss.msc.service.ServiceName;

import static java.util.Arrays.asList;

/**
 * Deployment unit processor which will pick up {@link EjbJarMetaData} attachment(s) from deployment unit
 * and create appropriate {@link org.jboss.as.ee.component.ComponentConfiguration}(s) for each enterprise bean
 * within the {@link org.jboss.metadata.ejb.spec.EjbJarMetaData}.
 * <p/>
 * EJB containers are deployed as Java EE Managed Beans (JSR-316). As such the {@link ComponentConfiguration} created
 * in this deployer will be corresponding to managed bean configurations whose bean class is an appropriate EJB component (for
 * example, {@link org.jboss.as.ejb3.component.stateless.StatelessSessionComponent})
 * <p/>
 * Author: Jaikiran Pai
 */
public class EjbComponentDeploymentUnitProcessor implements DeploymentUnitProcessor {

    /**
     * Logger
     */
    private static Logger logger = Logger.getLogger(EjbComponentDeploymentUnitProcessor.class);

    private static void addInterceptors(EJBComponentConfiguration componentConfiguration, JBossSessionBeanMetaData sessionBeanMetaData) {
        final JBossMetaData metaData = sessionBeanMetaData.getJBossMetaData();
        final InterceptorsMetaData interceptorsMetaData = metaData.getInterceptors();
        final JBossAssemblyDescriptorMetaData assemblyDescriptorMetaData = metaData.getAssemblyDescriptor();

        // Default interceptors
        for(final InterceptorBindingMetaData interceptorBindingMetaData : assemblyDescriptorMetaData.getInterceptorBindings()) {
            if(!interceptorBindingMetaData.isTotalOrdering() && interceptorBindingMetaData.getEjbName().equals("*")) {
                for(String interceptorClass : interceptorBindingMetaData.getInterceptorClasses()) {
                    // TODO: how to handle multiple methods?
                    final String methodName = interceptorsMetaData.get(interceptorClass).getAroundInvokes().get(0).getMethodName();
                    // TODO: fixme, choose the proper filter
                    final MethodInterceptorFilter methodFilter = MethodInterceptorAllFilter.INSTANCE;
                    componentConfiguration.addClassInterceptorConfig(new MethodInterceptorConfiguration(interceptorClass, methodName, methodFilter));
                }
            }
        }

        // Class interceptors
        //componentConfiguration.addClassInterceptorConfig();

        // BEan method interceptors
        if(sessionBeanMetaData.getAroundInvokes() != null) {
            for(final AroundInvokeMetaData aroundInvokeMetaData : sessionBeanMetaData.getAroundInvokes()) {
                String className = aroundInvokeMetaData.getClassName();
                if(className == null)
                    className = sessionBeanMetaData.getEjbClass();
                componentConfiguration.addComponentInterceptorConfig(new MethodInterceptorConfiguration(className, aroundInvokeMetaData.getMethodName(), MethodInterceptorAllFilter.INSTANCE));
            }
        }
    }

    private static void addLocalViews(EJBComponentConfiguration componentConfiguration, Iterable<String> viewClassNames) {
        if (viewClassNames == null)
            return;
        for (String viewClassName : viewClassNames)
            componentConfiguration.addViewClassName(viewClassName);
    }

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
            // attach a component config for stateless EJB component to deploy it as a Managed Bean
            this.createAndAttachManagedBeanComponentConfig(deploymentUnit, sessionBean);
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }


    private void createAndAttachManagedBeanComponentConfig(DeploymentUnit deploymentUnit, JBossSessionBeanMetaData sessionBean) {
        EJBComponentConfiguration sessionBeanComponentConfig;
        switch(sessionBean.getSessionType()) {
            case Stateless:
                sessionBeanComponentConfig = new EJBComponentConfiguration(sessionBean.getName(), sessionBean.getEjbClass(), new StatelessSessionComponentFactory());
                // setup the system interceptors
                this.setupSystemComponentInterceptors(sessionBeanComponentConfig);
                break;
            case Stateful:
                sessionBeanComponentConfig = new StatefulSessionComponentConfiguration(sessionBean.getName(), sessionBean.getEjbClass());
                break;
            default:
                logger.warn("Session type " + sessionBean.getSessionType() + " is not yet supported. Skipping " + sessionBean.getName() + " from deployment unit: " + deploymentUnit);
                return;
        }

        // setup the service names on the component config
        this.setupServiceNames(deploymentUnit, sessionBeanComponentConfig);

        addLocalViews(sessionBeanComponentConfig, sessionBean.getBusinessLocals());

        if (sessionBean instanceof JBossSessionBean31MetaData) {
            JBossSessionBean31MetaData sessionBean31 = (JBossSessionBean31MetaData) sessionBean;
            if (sessionBean31.isNoInterfaceBean())
                addLocalViews(sessionBeanComponentConfig, asList(sessionBean.getEjbClass()));
        }

        addInterceptors(sessionBeanComponentConfig, sessionBean);

        // add this component configuration as an attachment to the deployment unit
        deploymentUnit.addToAttachmentList(org.jboss.as.ee.component.Attachments.COMPONENT_CONFIGS, sessionBeanComponentConfig);
    }

    private void setupServiceNames(DeploymentUnit deploymentUnit, EJBComponentConfiguration componentConfiguration) {
        componentConfiguration.setAppContextServiceName(ContextServiceNameBuilder.app(deploymentUnit));

        ServiceName moduleContextServiceName = ContextServiceNameBuilder.module(deploymentUnit);
        componentConfiguration.setModuleContextServiceName(moduleContextServiceName);

        ServiceName ejbCompServiceName = this.getEjbCompServiceName(deploymentUnit, componentConfiguration.getName());
        componentConfiguration.setCompContextServiceName(ejbCompServiceName);

        componentConfiguration.setEnvContextServiceName(ejbCompServiceName.append("env"));
    }

    private ServiceName getEjbCompServiceName(DeploymentUnit deploymentUnit, String ejbName) {
        DeploymentUnit parent = deploymentUnit.getParent();
        if (parent == null) {
            return ContextNames.COMPONENT_CONTEXT_SERVICE_NAME.append(deploymentUnit.getName()).append(ejbName);
        }
        return ContextNames.COMPONENT_CONTEXT_SERVICE_NAME.append(parent.getName()).append(deploymentUnit.getName()).append(ejbName);
    }

    private void setupSystemComponentInterceptors(ComponentConfiguration componentConfiguration) {
        // TODO: Dummy for now. Should be configurable set of system interceptors, later.
        componentConfiguration.addComponentSystemInterceptorFactory(new ImmediateInterceptorFactory(new DummyComponentInterceptor()));
    }
}
