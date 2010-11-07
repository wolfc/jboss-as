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

package org.jboss.as.connector.deployers;

import org.jboss.as.connector.ConnectorServices;
import org.jboss.as.connector.deployers.processors.DsDependencyProcessor;
import org.jboss.as.connector.deployers.processors.DsDeploymentProcessor;
import org.jboss.as.connector.deployers.processors.IronJacamarDeploymentParsingProcessor;
import org.jboss.as.connector.deployers.processors.ParsedRaDeploymentProcessor;
import org.jboss.as.connector.deployers.processors.RaDeploymentParsingProcessor;
import org.jboss.as.connector.deployers.processors.RaXmlDeploymentProcessor;
import org.jboss.as.connector.deployers.processors.RarConfigProcessor;
import org.jboss.as.connector.jndi.JndiStrategyService;
import org.jboss.as.connector.mdr.MdrService;
import org.jboss.as.connector.registry.ResourceAdapterDeploymentRegistry;
import org.jboss.as.connector.registry.ResourceAdapterDeploymentRegistryService;
import org.jboss.as.connector.subsystems.connector.ConnectorSubsystemConfiguration;
import org.jboss.as.deployment.chain.DeploymentChain;
import org.jboss.as.deployment.chain.DeploymentChainImpl;
import org.jboss.as.deployment.chain.DeploymentChainProcessorInjector;
import org.jboss.as.deployment.chain.DeploymentChainProvider;
import org.jboss.as.deployment.chain.DeploymentChainProviderInjector;
import org.jboss.as.deployment.chain.DeploymentChainProviderService;
import org.jboss.as.deployment.chain.DeploymentChainService;
import org.jboss.as.deployment.chain.JarDeploymentActivator;
import org.jboss.as.deployment.module.DeploymentModuleLoader;
import org.jboss.as.deployment.module.DeploymentModuleLoaderProcessor;
import org.jboss.as.deployment.module.DeploymentModuleLoaderService;
import org.jboss.as.deployment.module.ManifestAttachmentProcessor;
import org.jboss.as.deployment.module.ModuleConfigProcessor;
import org.jboss.as.deployment.module.ModuleDependencyProcessor;
import org.jboss.as.deployment.module.ModuleDeploymentProcessor;
import org.jboss.as.deployment.module.NestedJarInlineProcessor;
import org.jboss.as.deployment.naming.ModuleContextProcessor;
import org.jboss.as.deployment.processor.AnnotationIndexProcessor;
import org.jboss.as.deployment.unit.DeploymentUnitProcessor;
import org.jboss.as.deployment.unit.DeploymentUnitProcessorService;
import org.jboss.as.naming.service.NamingService;
import org.jboss.as.txn.TxnServices;
import org.jboss.jca.common.api.metadata.ds.DataSources;
import org.jboss.jca.common.api.metadata.resourceadapter.ResourceAdapters;
import org.jboss.jca.core.spi.naming.JndiStrategy;
import org.jboss.jca.core.spi.mdr.MetadataRepository;
import org.jboss.msc.service.BatchBuilder;
import org.jboss.msc.service.BatchServiceBuilder;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.InjectedValue;
import org.jboss.msc.value.Value;
import org.jboss.msc.value.Values;

/**
 * Service activator which installs the various service required for rar
 * deployments.
 * @author <a href="mailto:stefano.maestri@redhat.com">Stefano Maestri</a>
 */
public class RaDeploymentActivator implements ServiceActivator {

    public static final long RAR_DEPLOYMENT_CHAIN_PRIORITY = JarDeploymentActivator.JAR_DEPLOYMENT_CHAIN_PRIORITY - 100;
    public static final ServiceName RAR_DEPLOYMENT_CHAIN_SERVICE_NAME = DeploymentChain.SERVICE_NAME.append("rar");

    /**
     * Activate the services required for service deployments.
     * @param context The service activator context
     */
    @Override
    public void activate(final ServiceActivatorContext context) {

        final BatchBuilder batchBuilder = context.getBatchBuilder();
        batchBuilder.addServiceValueIfNotExist(DeploymentChainProviderService.SERVICE_NAME,
                new DeploymentChainProviderService());

        final Value<DeploymentChain> deploymentChainValue = Values.immediateValue((DeploymentChain) new DeploymentChainImpl(
                RAR_DEPLOYMENT_CHAIN_SERVICE_NAME.toString()));
        final DeploymentChainService deploymentChainService = new DeploymentChainService(deploymentChainValue);
        batchBuilder.addService(RAR_DEPLOYMENT_CHAIN_SERVICE_NAME, deploymentChainService).addDependency(
                DeploymentChainProviderService.SERVICE_NAME,
                DeploymentChainProvider.class,
                new DeploymentChainProviderInjector<DeploymentChain>(deploymentChainValue, new RaDeploymentChainSelector(),
                        RAR_DEPLOYMENT_CHAIN_PRIORITY));

        addDeploymentProcessor(batchBuilder, new NestedJarInlineProcessor(), NestedJarInlineProcessor.PRIORITY);
        addDeploymentProcessor(batchBuilder, new ManifestAttachmentProcessor(), ManifestAttachmentProcessor.PRIORITY);
        addDeploymentProcessor(batchBuilder, new AnnotationIndexProcessor(), AnnotationIndexProcessor.PRIORITY);
        addDeploymentProcessor(batchBuilder, new RarConfigProcessor(), RarConfigProcessor.PRIORITY);

        addDeploymentProcessor(batchBuilder, new ModuleDependencyProcessor(), ModuleDependencyProcessor.PRIORITY);
        addDeploymentProcessor(batchBuilder, new ModuleConfigProcessor(), ModuleConfigProcessor.PRIORITY);

        // add resources here
        final InjectedValue<DeploymentModuleLoader> deploymentModuleLoaderValue = new InjectedValue<DeploymentModuleLoader>();
        DeploymentModuleLoaderProcessor deploymentLoaderProcessor = new DeploymentModuleLoaderProcessor(
                deploymentModuleLoaderValue);
        addDeploymentProcessor(batchBuilder, deploymentLoaderProcessor, DeploymentModuleLoaderProcessor.PRIORITY)
                .addDependency(DeploymentModuleLoaderService.SERVICE_NAME, DeploymentModuleLoader.class,
                        deploymentModuleLoaderValue);
        addDeploymentProcessor(batchBuilder, new ModuleDeploymentProcessor(), ModuleDeploymentProcessor.PRIORITY);
        addDeploymentProcessor(batchBuilder, new ModuleContextProcessor(), ModuleContextProcessor.PRIORITY);

        MdrService mdrService = new MdrService();
        batchBuilder.addService(ConnectorServices.IRONJACAMAR_MDR, mdrService);

        ResourceAdapterDeploymentRegistryService registryService = new ResourceAdapterDeploymentRegistryService();
        batchBuilder.addService(ConnectorServices.RESOURCE_ADAPTER_REGISTRY_SERVICE, registryService);

        JndiStrategyService jndiStrategyService = new JndiStrategyService();
        batchBuilder.addService(ConnectorServices.JNDI_STRATEGY_SERVICE, jndiStrategyService);

        RaDeploymentParsingProcessor raDeploymentParsingProcessor = new RaDeploymentParsingProcessor();
        addDeploymentProcessor(batchBuilder, raDeploymentParsingProcessor, RaDeploymentParsingProcessor.PRIORITY);

        IronJacamarDeploymentParsingProcessor ironJacamarDeploymentParsingProcessor = new IronJacamarDeploymentParsingProcessor();
        addDeploymentProcessor(batchBuilder, ironJacamarDeploymentParsingProcessor,
                IronJacamarDeploymentParsingProcessor.PRIORITY).addDependency(ConnectorServices.IRONJACAMAR_MDR,
                MetadataRepository.class, ironJacamarDeploymentParsingProcessor.getMdrInjector());

        ParsedRaDeploymentProcessor parsedRaDeploymentProcessor = new ParsedRaDeploymentProcessor();
        addDeploymentProcessor(batchBuilder, parsedRaDeploymentProcessor, ParsedRaDeploymentProcessor.PRIORITY)
                .addDependency(TxnServices.JBOSS_TXN_ARJUNA_TRANSACTION_MANAGER,
                        com.arjuna.ats.jbossatx.jta.TransactionManagerService.class,
                        parsedRaDeploymentProcessor.getTxmInjector())
                .addDependency(ConnectorServices.IRONJACAMAR_MDR, MetadataRepository.class,
                        parsedRaDeploymentProcessor.getMdrInjector())
                .addDependency(ConnectorServices.RESOURCE_ADAPTER_REGISTRY_SERVICE, ResourceAdapterDeploymentRegistry.class,
                        parsedRaDeploymentProcessor.getRegistryInjector())
                .addDependency(ConnectorServices.CONNECTOR_CONFIG_SERVICE, ConnectorSubsystemConfiguration.class,
                        parsedRaDeploymentProcessor.getConfigInjector()).addDependency(NamingService.SERVICE_NAME)
                .addDependency(ConnectorServices.JNDI_STRATEGY_SERVICE, JndiStrategy.class,
                        parsedRaDeploymentProcessor.getJndiInjector())
                .addDependency(ConnectorServices.DEFAULT_BOOTSTRAP_CONTEXT_SERVICE);

        RaXmlDeploymentProcessor raXmlDeploymentProcessor = new RaXmlDeploymentProcessor();
        addDeploymentProcessor(batchBuilder, raXmlDeploymentProcessor, RaXmlDeploymentProcessor.PRIORITY)
                .addDependency(TxnServices.JBOSS_TXN_ARJUNA_TRANSACTION_MANAGER,
                        com.arjuna.ats.jbossatx.jta.TransactionManagerService.class, raXmlDeploymentProcessor.getTxmInjector())
                .addDependency(ConnectorServices.IRONJACAMAR_MDR, MetadataRepository.class, raXmlDeploymentProcessor.getMdrInjector())
                .addDependency(ConnectorServices.RESOURCE_ADAPTER_REGISTRY_SERVICE, ResourceAdapterDeploymentRegistry.class,
                        raXmlDeploymentProcessor.getRegistryInjector())
                .addDependency(ConnectorServices.CONNECTOR_CONFIG_SERVICE, ConnectorSubsystemConfiguration.class,
                        raXmlDeploymentProcessor.getConfigInjector())
                .addDependency(ConnectorServices.RESOURCEADAPTERS_SERVICE, ResourceAdapters.class,
                        raXmlDeploymentProcessor.getRaxmlValueInjector()).addDependency(NamingService.SERVICE_NAME)
                .addDependency(ConnectorServices.JNDI_STRATEGY_SERVICE, JndiStrategy.class,
                        raXmlDeploymentProcessor.getJndiInjector())
                .addDependency(ConnectorServices.DEFAULT_BOOTSTRAP_CONTEXT_SERVICE);

        DsDependencyProcessor dsDependencyProcessor = new DsDependencyProcessor();
        addDeploymentProcessor(batchBuilder, dsDependencyProcessor, DsDependencyProcessor.PRIORITY)
                .addDependency(ConnectorServices.DATASOURCES_SERVICE, DataSources.class,
                        dsDependencyProcessor.getDsValueInjector()).setInitialMode(Mode.ACTIVE);

        DsDeploymentProcessor dsDeploymentProcessor = new DsDeploymentProcessor();
        addDeploymentProcessor(batchBuilder, dsDeploymentProcessor, DsDeploymentProcessor.PRIORITY)
                .addDependency(TxnServices.JBOSS_TXN_ARJUNA_TRANSACTION_MANAGER,
                        com.arjuna.ats.jbossatx.jta.TransactionManagerService.class, dsDeploymentProcessor.getTxmInjector())
                .addDependency(ConnectorServices.IRONJACAMAR_MDR, MetadataRepository.class, dsDeploymentProcessor.getMdrInjector())
                .addDependency(ConnectorServices.RESOURCE_ADAPTER_REGISTRY_SERVICE, ResourceAdapterDeploymentRegistry.class,
                        dsDeploymentProcessor.getRegistryInjector())
                .addDependency(ConnectorServices.JNDI_STRATEGY_SERVICE, JndiStrategy.class,
                        dsDeploymentProcessor.getJndiInjector())
                .addDependency(ConnectorServices.DATASOURCES_SERVICE, DataSources.class,
                        dsDeploymentProcessor.getDsValueInjector()).setInitialMode(Mode.ACTIVE);

    }

    private <T extends DeploymentUnitProcessor> BatchServiceBuilder<T> addDeploymentProcessor(final BatchBuilder batchBuilder,
            final T deploymentUnitProcessor, final long priority) {
        final DeploymentUnitProcessorService<T> deploymentUnitProcessorService = new DeploymentUnitProcessorService<T>(
                deploymentUnitProcessor);
        return batchBuilder.addService(RAR_DEPLOYMENT_CHAIN_SERVICE_NAME.append(deploymentUnitProcessor.getClass().getName()),
                deploymentUnitProcessorService).addDependency(RAR_DEPLOYMENT_CHAIN_SERVICE_NAME, DeploymentChain.class,
                new DeploymentChainProcessorInjector<T>(deploymentUnitProcessorService, priority));
    }
}
