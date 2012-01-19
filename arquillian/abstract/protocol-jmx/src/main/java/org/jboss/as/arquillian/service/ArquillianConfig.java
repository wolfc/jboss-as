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
package org.jboss.as.arquillian.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.arquillian.testenricher.msc.ServiceContainerAssociation;
import org.jboss.arquillian.testenricher.msc.ServiceTargetAssociation;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.modules.Module;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * The ArquillianConfig represents an Arquillian deployment.
 *
 * @author Thomas.Diesler@jboss.com
 */
public class ArquillianConfig<S extends ArquillianConfig> implements Service<S> {

    static final AttachmentKey<ArquillianConfig> KEY = AttachmentKey.create(ArquillianConfig.class);

    private final ArquillianService arqService;
    private final DeploymentUnit depUnit;
    private final ServiceName serviceName;
    private final List<String> testClasses = new ArrayList<String>();

    private ServiceContainer serviceContainer;
    private ServiceTarget serviceTarget;

    static ServiceName getServiceName(DeploymentUnit depUnit) {
        return ServiceName.JBOSS.append("arquillian", "config", depUnit.getName());
    }

    protected ArquillianConfig(ArquillianService arqService, DeploymentUnit depUnit, Set<String> testClasses) {
        this.arqService = arqService;
        this.depUnit = depUnit;
        this.serviceName = getServiceName(depUnit);
        this.testClasses.addAll(testClasses);
    }

    protected void associate() {
        // Always make the MSC artifacts available
        ServiceTargetAssociation.setServiceTarget(serviceTarget);
        ServiceContainerAssociation.setServiceContainer(serviceContainer);
    }

    ServiceBuilder<S> buildService(ServiceTarget serviceTarget, ServiceController<?> depController) {
        ServiceBuilder<S> builder = serviceTarget.addService(getServiceName(), this);
        builder.addDependency(depController.getName());
        return builder;
    }

    protected ArquillianService getArquillianService() {
        return arqService;
    }

    protected ServiceName getServiceName() {
        return serviceName;
    }

    protected DeploymentUnit getDeploymentUnit() {
        return depUnit;
    }

    protected Collection<String> getTestClasses() {
        return Collections.unmodifiableList(testClasses);
    }

    Class<?> loadClass(String className) throws ClassNotFoundException {

        if (testClasses.contains(className) == false)
            throw new ClassNotFoundException("Class '" + className + "' not found in: " + testClasses);

        final Module module = depUnit.getAttachment(Attachments.MODULE);
        if (module == null)
            throw new IllegalStateException("Cannot determine deployment type: " + depUnit);
        final Class<?> testClass = module.getClassLoader().loadClass(className);

        associate();

        return testClass;
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        serviceContainer = context.getController().getServiceContainer();
        serviceTarget = context.getChildTarget();
        arqService.registerArquillianConfig(this);
    }

    @Override
    public synchronized void stop(StopContext context) {
        context.getController().setMode(Mode.REMOVE);
        arqService.unregisterArquillianConfig(this);
    }

    @Override
    public synchronized S getValue() {
        return (S) this;
    }

    @Override
    public String toString() {
        String uname = depUnit.getName();
        String sname = serviceName.getCanonicalName();
        return "ArquillianConfig[service=" + sname + ",unit=" + uname + ",tests=" + testClasses + "]";
    }
}
