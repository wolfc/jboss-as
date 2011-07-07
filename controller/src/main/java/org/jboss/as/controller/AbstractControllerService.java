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

package org.jboss.as.controller;

import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A base class for controller services.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractControllerService implements Service<ModelController> {
    private static final Logger log = Logger.getLogger(AbstractControllerService.class);

    private final OperationContext.Type controllerType;
    private final ConfigurationPersister configurationPersister;
    private final DescriptionProvider rootDescriptionProvider;
    private final ControlledProcessState processState;
    private final OperationStepHandler prepareStep;
    private volatile ModelControllerImpl controller;

    /**
     * Construct a new instance.
     *
     * @param controllerType the controller type for the new controller
     * @param configurationPersister the configuration persister
     * @param rootDescriptionProvider the root description provider
     * @param prepareStep the prepare step to prepend to operation execution
     */
    protected AbstractControllerService(final OperationContext.Type controllerType, final ConfigurationPersister configurationPersister,
                                        final ControlledProcessState processState, final DescriptionProvider rootDescriptionProvider, final OperationStepHandler prepareStep) {
        this.controllerType = controllerType;
        this.configurationPersister = configurationPersister;
        this.rootDescriptionProvider = rootDescriptionProvider;
        this.processState = processState;
        this.prepareStep = prepareStep;
    }

    public void start(final StartContext context) throws StartException {
        final ServiceController<?> serviceController = context.getController();
        final ServiceContainer container = serviceController.getServiceContainer();
//        final ContainerStateMonitor containerStateMonitor = null;
        final ContainerStateMonitor containerStateMonitor = new ContainerStateMonitor(container, serviceController);
        final ModelControllerImpl controller = new ModelControllerImpl(container, context.getChildTarget(), ManagementResourceRegistration.Factory.create(rootDescriptionProvider), containerStateMonitor, configurationPersister, controllerType, prepareStep, processState);
        initModel(controller.getRootResource(), controller.getRootRegistration());
        this.controller = controller;

        final ServiceTarget target = context.getChildTarget();
        new Thread(new Runnable() {
            public void run() {
                try {
                    boot(new BootContext() {
                        public ServiceTarget getServiceTarget() {
                            return target;
                        }
                    });
                } catch (ConfigurationPersistenceException e) {
                    throw new RuntimeException(e);
                } finally {
                    processState.setRunning();
                }
            }
        }, "Controller Boot Thread").start();
    }

    /**
     * Boot the controller.  Called during service start.
     *
     * @param context the boot context
     * @throws ConfigurationPersistenceException if the configuration failed to be loaded
     */
    protected void boot(final BootContext context) throws ConfigurationPersistenceException {
        log.info("Boot at " + System.currentTimeMillis());
        final ModelNode done = new ModelNode();
        final BlockingQueue<ModelNode> bootOperations = new LinkedBlockingQueue<ModelNode>();
        final FutureTask<Void> task = new FutureTask<Void>(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                configurationPersister.load(bootOperations);
                bootOperations.put(done);
                return null;
            }
        });
        Thread thread = new Thread(task, "Parsing thread");
        thread.start();
        log.info("Parsing done at " + System.currentTimeMillis());
        boot(bootOperations, done);
        finishBoot();
    }

    protected void boot(BlockingQueue<ModelNode> bootOperations, final ModelNode done) throws ConfigurationPersistenceException {
        controller.boot(bootOperations, done, OperationMessageHandler.logging, ModelController.OperationTransactionControl.COMMIT);
    }

    protected void finishBoot() throws ConfigurationPersistenceException {
        controller.finshBoot();
        configurationPersister.successfulBoot();
    }

    public void stop(final StopContext context) {
        controller = null;
    }

    public ModelController getValue() throws IllegalStateException, IllegalArgumentException {
        final ModelController controller = this.controller;
        if (controller == null) {
            throw new IllegalStateException();
        }
        return controller;
    }

    protected abstract void initModel(Resource rootResource, ManagementResourceRegistration rootRegistration);
}
