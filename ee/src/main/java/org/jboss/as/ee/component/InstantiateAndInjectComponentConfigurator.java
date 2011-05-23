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
package org.jboss.as.ee.component;

import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ValueManagedReferenceFactory;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndex;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndexUtil;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.Interceptors;
import org.jboss.invocation.proxy.MethodIdentifier;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.value.ConstructedValue;
import org.jboss.msc.value.InjectedValue;
import org.jboss.msc.value.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.jboss.as.server.deployment.Attachments.REFLECTION_INDEX;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class InstantiateAndInjectComponentConfigurator extends AbstractComponentConfiguratorProcessor {
    private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];

    private static class InjectedConfigurator implements DependencyConfigurator {

        private final ResourceInjectionConfiguration injectionConfiguration;
        private final ComponentConfiguration configuration;
        private final DeploymentPhaseContext context;
        private final InjectedValue<ManagedReferenceFactory> managedReferenceFactoryValue;

        InjectedConfigurator(final ResourceInjectionConfiguration injectionConfiguration, final ComponentConfiguration configuration, final DeploymentPhaseContext context, final InjectedValue<ManagedReferenceFactory> managedReferenceFactoryValue) {
            this.injectionConfiguration = injectionConfiguration;
            this.configuration = configuration;
            this.context = context;
            this.managedReferenceFactoryValue = managedReferenceFactoryValue;
        }

        public void configureDependency(final ServiceBuilder<?> serviceBuilder) throws DeploymentUnitProcessingException {
            InjectionSource.ResolutionContext resolutionContext = new InjectionSource.ResolutionContext(
                    configuration.getComponentDescription().getNamingMode() == ComponentNamingMode.USE_MODULE,
                    configuration.getComponentName(),
                    configuration.getModuleName(),
                    configuration.getApplicationName()
            );
            injectionConfiguration.getSource().getResourceValue(resolutionContext, serviceBuilder, context, managedReferenceFactoryValue);
        }
    }

    @Override
    public void configure(final DeploymentPhaseContext context, final ComponentDescription description, final ComponentConfiguration configuration) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = context.getDeploymentUnit();
        final DeploymentReflectionIndex deploymentReflectionIndex = deploymentUnit.getAttachment(REFLECTION_INDEX);
        final Object instanceKey = BasicComponentInstance.INSTANCE_KEY;
        final Module module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);

        // Module stuff
        final EEModuleClassConfiguration componentClassConfiguration = configuration.getModuleClassConfiguration();
        final EEModuleConfiguration moduleConfiguration = componentClassConfiguration.getModuleConfiguration();

        final Deque<InterceptorFactory> instantiators = new ArrayDeque<InterceptorFactory>();
        final Deque<InterceptorFactory> injectors = new ArrayDeque<InterceptorFactory>();
        final Deque<InterceptorFactory> uninjectors = new ArrayDeque<InterceptorFactory>();
        final Deque<InterceptorFactory> destructors = new ArrayDeque<InterceptorFactory>();

        final ClassReflectionIndex<?> componentClassIndex = deploymentReflectionIndex.getClassIndex(componentClassConfiguration.getModuleClass());
        final List<InterceptorFactory> componentUserAroundInvoke = new ArrayList<InterceptorFactory>();
        final Map<String, List<InterceptorFactory>> userAroundInvokesByInterceptorClass = new HashMap<String, List<InterceptorFactory>>();

        final Map<String, List<InterceptorFactory>> userPostConstructByInterceptorClass = new HashMap<String, List<InterceptorFactory>>();
        final Map<String, List<InterceptorFactory>> userPreDestroyByInterceptorClass = new HashMap<String, List<InterceptorFactory>>();

        // Primary instance
        final ManagedReferenceFactory instanceFactory = configuration.getInstanceFactory();
        if (instanceFactory != null) {
            instantiators.addFirst(new ManagedReferenceInterceptorFactory(instanceFactory, instanceKey));
        } else {
            //use the default constructor if no instanceFactory has been set
            ValueManagedReferenceFactory factory = new ValueManagedReferenceFactory(new ConstructedValue<Object>((Constructor<Object>) componentClassIndex.getConstructor(EMPTY_CLASS_ARRAY), Collections.<Value<?>>emptyList()));
            instantiators.addFirst(new ManagedReferenceInterceptorFactory(factory, instanceKey));
        }
        destructors.addLast(new ManagedReferenceReleaseInterceptorFactory(instanceKey));

        new ClassDescriptionTraversal(componentClassConfiguration, moduleConfiguration) {

            @Override
            public void handle(EEModuleClassConfiguration classConfiguration, EEModuleClassDescription classDescription) throws DeploymentUnitProcessingException {
                for (final ResourceInjectionConfiguration injectionConfiguration : classConfiguration.getInjectionConfigurations()) {
                    final Object valueContextKey = new Object();
                    final InjectedValue<ManagedReferenceFactory> managedReferenceFactoryValue = new InjectedValue<ManagedReferenceFactory>();
                    configuration.getStartDependencies().add(new InjectedConfigurator(injectionConfiguration, configuration, context, managedReferenceFactoryValue));
                    injectors.addFirst(injectionConfiguration.getTarget().createInjectionInterceptorFactory(instanceKey, valueContextKey, managedReferenceFactoryValue, deploymentUnit));
                    uninjectors.addLast(new ManagedReferenceReleaseInterceptorFactory(valueContextKey));
                }
            }
        }.run();


        //all interceptors with lifecycle callbacks, in the correct order
        final LinkedHashSet<InterceptorDescription> interceptorWithLifecycleCallbacks = new LinkedHashSet<InterceptorDescription>();
        if (!description.isExcludeDefaultInterceptors()) {
            interceptorWithLifecycleCallbacks.addAll(description.getDefaultInterceptors());
        }
        interceptorWithLifecycleCallbacks.addAll(description.getClassInterceptors());

        for (final InterceptorDescription interceptorDescription : description.getAllInterceptors()) {
            final String interceptorClassName = interceptorDescription.getInterceptorClassName();
            final EEModuleClassConfiguration interceptorConfiguration = moduleConfiguration.getClassConfiguration(interceptorClassName);

            //we store the interceptor instance under the class key
            final Object contextKey = interceptorConfiguration.getModuleClass();
            if (interceptorConfiguration.getInstantiator() == null) {
                throw new DeploymentUnitProcessingException("No default constructor for interceptor class " + interceptorClassName + " on component " + componentClassConfiguration.getModuleClass());
            }
            instantiators.addFirst(new ManagedReferenceInterceptorFactory(interceptorConfiguration.getInstantiator(), contextKey));
            destructors.addLast(new ManagedReferenceReleaseInterceptorFactory(contextKey));

            final boolean interceptorHasLifecycleCallbacks = interceptorWithLifecycleCallbacks.contains(interceptorDescription);
            final ClassReflectionIndex<?> interceptorIndex = deploymentReflectionIndex.getClassIndex(interceptorConfiguration.getModuleClass());

            new ClassDescriptionTraversal(interceptorConfiguration, moduleConfiguration) {
                @Override
                public void handle(EEModuleClassConfiguration interceptorClassConfiguration, EEModuleClassDescription classDescription) throws DeploymentUnitProcessingException {
                    final ClassReflectionIndex<?> interceptorClassIndex = deploymentReflectionIndex.getClassIndex(interceptorClassConfiguration.getModuleClass());

                    for (final ResourceInjectionConfiguration injectionConfiguration : interceptorClassConfiguration.getInjectionConfigurations()) {
                        final Object valueContextKey = new Object();
                        final InjectedValue<ManagedReferenceFactory> managedReferenceFactoryValue = new InjectedValue<ManagedReferenceFactory>();
                        configuration.getStartDependencies().add(new InjectedConfigurator(injectionConfiguration, configuration, context, managedReferenceFactoryValue));
                        injectors.addFirst(injectionConfiguration.getTarget().createInjectionInterceptorFactory(contextKey, valueContextKey, managedReferenceFactoryValue, deploymentUnit));
                        uninjectors.addLast(new ManagedReferenceReleaseInterceptorFactory(valueContextKey));
                    }
                    // Only class level interceptors are processed for postconstruct/predestroy methods.
                    // Method level interceptors aren't supposed to be processed for postconstruct/predestroy lifecycle
                    // methods, as per interceptors spec
                    if (interceptorHasLifecycleCallbacks) {
                        final MethodIdentifier postConstructMethodIdentifier = classDescription.getPostConstructMethod();
                        if (postConstructMethodIdentifier != null) {
                            final Method method = ClassReflectionIndexUtil.findRequiredMethod(deploymentReflectionIndex, interceptorClassIndex, postConstructMethodIdentifier);

                            if (isNotOverriden(interceptorClassConfiguration, method, interceptorIndex, deploymentReflectionIndex)) {
                                InterceptorFactory interceptorFactory = new ManagedReferenceLifecycleMethodInterceptorFactory(contextKey, method, true);
                                List<InterceptorFactory> userPostConstruct = userPostConstructByInterceptorClass.get(interceptorClassName);
                                if (userPostConstruct == null) {
                                    userPostConstructByInterceptorClass.put(interceptorClassName, userPostConstruct = new ArrayList<InterceptorFactory>());
                                }
                                userPostConstruct.add(interceptorFactory);
                            }
                        }
                        final MethodIdentifier preDestroyMethodIdentifier = classDescription.getPreDestroyMethod();
                        if (preDestroyMethodIdentifier != null) {
                            final Method method = ClassReflectionIndexUtil.findRequiredMethod(deploymentReflectionIndex, interceptorClassIndex, preDestroyMethodIdentifier);
                            if (isNotOverriden(interceptorClassConfiguration, method, interceptorIndex, deploymentReflectionIndex)) {
                                InterceptorFactory interceptorFactory = new ManagedReferenceLifecycleMethodInterceptorFactory(contextKey, method, true);
                                List<InterceptorFactory> userPreDestroy = userPreDestroyByInterceptorClass.get(interceptorClassName);
                                if (userPreDestroy == null) {
                                    userPreDestroyByInterceptorClass.put(interceptorClassName, userPreDestroy = new ArrayList<InterceptorFactory>());
                                }
                                userPreDestroy.add(interceptorFactory);
                            }
                        }
                    }
                    final MethodIdentifier aroundInvokeMethodIdentifier = classDescription.getAroundInvokeMethod();
                    if (aroundInvokeMethodIdentifier != null) {
                        final Method method = ClassReflectionIndexUtil.findRequiredMethod(deploymentReflectionIndex, interceptorClassIndex, aroundInvokeMethodIdentifier);
                        if (isNotOverriden(interceptorClassConfiguration, method, interceptorIndex, deploymentReflectionIndex)) {
                            List<InterceptorFactory> interceptors;
                            if ((interceptors = userAroundInvokesByInterceptorClass.get(interceptorClassName)) == null) {
                                userAroundInvokesByInterceptorClass.put(interceptorClassName, interceptors = new ArrayList<InterceptorFactory>());
                            }
                            interceptors.add(new ManagedReferenceLifecycleMethodInterceptorFactory(contextKey, method, false));
                        }
                    }
                }
            }.run();
        }

        final Deque<InterceptorFactory> userPostConstruct = new ArrayDeque<InterceptorFactory>();
        final Deque<InterceptorFactory> userPreDestroy = new ArrayDeque<InterceptorFactory>();

        //now add the lifecycle interceptors in the correct order


        for (final InterceptorDescription interceptorClass : interceptorWithLifecycleCallbacks) {
            if (userPostConstructByInterceptorClass.containsKey(interceptorClass.getInterceptorClassName())) {
                userPostConstruct.addAll(userPostConstructByInterceptorClass.get(interceptorClass.getInterceptorClassName()));
            }
            if (userPreDestroyByInterceptorClass.containsKey(interceptorClass.getInterceptorClassName())) {
                userPreDestroy.addAll(userPreDestroyByInterceptorClass.get(interceptorClass.getInterceptorClassName()));
            }
        }


        new ClassDescriptionTraversal(componentClassConfiguration, moduleConfiguration) {
            @Override
            public void handle(EEModuleClassConfiguration configuration, EEModuleClassDescription classDescription) throws DeploymentUnitProcessingException {
                final ClassReflectionIndex classReflectionIndex = deploymentReflectionIndex.getClassIndex(configuration.getModuleClass());
                final MethodIdentifier componentPostConstructMethodIdentifier = classDescription.getPostConstructMethod();
                if (componentPostConstructMethodIdentifier != null) {
                    final Method method = ClassReflectionIndexUtil.findRequiredMethod(deploymentReflectionIndex, classReflectionIndex, componentPostConstructMethodIdentifier);
                    if (isNotOverriden(configuration, method, componentClassIndex, deploymentReflectionIndex)) {
                        InterceptorFactory interceptorFactory = new ManagedReferenceLifecycleMethodInterceptorFactory(instanceKey, method, true);
                        userPostConstruct.addLast(interceptorFactory);
                    }
                }
                final MethodIdentifier componentPreDestroyMethodIdentifier = classDescription.getPreDestroyMethod();
                if (componentPreDestroyMethodIdentifier != null) {
                    final Method method = ClassReflectionIndexUtil.findRequiredMethod(deploymentReflectionIndex, classReflectionIndex, componentPreDestroyMethodIdentifier);
                    if (isNotOverriden(configuration, method, componentClassIndex, deploymentReflectionIndex)) {
                        InterceptorFactory interceptorFactory = new ManagedReferenceLifecycleMethodInterceptorFactory(instanceKey, method, true);
                        userPreDestroy.addLast(interceptorFactory);
                    }
                }
                final MethodIdentifier componentAroundInvokeMethodIdentifier = classDescription.getAroundInvokeMethod();
                if (componentAroundInvokeMethodIdentifier != null) {
                    final Method method = ClassReflectionIndexUtil.findRequiredMethod(deploymentReflectionIndex, classReflectionIndex, componentAroundInvokeMethodIdentifier);

                    if (isNotOverriden(configuration, method, componentClassIndex, deploymentReflectionIndex)) {
                        componentUserAroundInvoke.add(new ManagedReferenceLifecycleMethodInterceptorFactory(instanceKey, method, false));
                    }
                }
            }
        }.run();

        // Apply post-construct
        final Queue<InterceptorFactory> postConstructInterceptors = configuration.getPostConstructInterceptors();
        final Iterator<InterceptorFactory> instantiatorIterator = instantiators.iterator();
        while (instantiatorIterator.hasNext()) {
            postConstructInterceptors.add(instantiatorIterator.next());
        }
        final Iterator<InterceptorFactory> injectorIterator = injectors.iterator();
        while (injectorIterator.hasNext()) {
            postConstructInterceptors.add(injectorIterator.next());
        }
        postConstructInterceptors.addAll(userPostConstruct);
        postConstructInterceptors.add(Interceptors.getTerminalInterceptorFactory());

        // Apply pre-destroy
        final Queue<InterceptorFactory> preDestroyInterceptors = configuration.getPreDestroyInterceptors();
        final Iterator<InterceptorFactory> uninjectorsIterator = uninjectors.descendingIterator();
        while (uninjectorsIterator.hasNext()) {
            preDestroyInterceptors.add(uninjectorsIterator.next());
        }
        final Iterator<InterceptorFactory> destructorIterator = destructors.descendingIterator();
        while (destructorIterator.hasNext()) {
            preDestroyInterceptors.add(destructorIterator.next());
        }
        preDestroyInterceptors.addAll(userPreDestroy);
        preDestroyInterceptors.add(Interceptors.getTerminalInterceptorFactory());

        // @AroundInvoke interceptors
        final List<InterceptorDescription> classInterceptors = description.getClassInterceptors();
        final Map<MethodIdentifier, List<InterceptorDescription>> methodInterceptors = description.getMethodInterceptors();

        Class clazz = componentClassConfiguration.getModuleClass();
        while (clazz != null) {
            final ClassReflectionIndex classIndex = deploymentReflectionIndex.getClassIndex(clazz);
            for (final Method method : (Collection<Method>) classIndex.getMethods()) {
                MethodIdentifier identifier = MethodIdentifier.getIdentifier(method.getReturnType(), method.getName(), method.getParameterTypes());
                Queue<InterceptorFactory> interceptorDeque = configuration.getComponentInterceptorDeque(method);

                // first add the default interceptors (if not excluded) to the deque
                if (!description.isExcludeDefaultInterceptors() && !description.isExcludeDefaultInterceptors(identifier)) {
                    for (InterceptorDescription interceptorDescription : description.getDefaultInterceptors()) {
                        String interceptorClassName = interceptorDescription.getInterceptorClassName();
                        List<InterceptorFactory> aroundInvokes = userAroundInvokesByInterceptorClass.get(interceptorClassName);
                        if (aroundInvokes != null) {
                            interceptorDeque.addAll(aroundInvokes);
                        }
                    }
                }

                // now add class level interceptors (if not excluded) to the deque
                if (!description.isExcludeClassInterceptors(identifier)) {
                    for (InterceptorDescription interceptorDescription : classInterceptors) {
                        String interceptorClassName = interceptorDescription.getInterceptorClassName();
                        List<InterceptorFactory> aroundInvokes = userAroundInvokesByInterceptorClass.get(interceptorClassName);
                        if (aroundInvokes != null) {
                            interceptorDeque.addAll(aroundInvokes);
                        }
                    }
                }

                // now add method level interceptors for to the deque so that they are triggered after the class interceptors
                List<InterceptorDescription> methodLevelInterceptors = methodInterceptors.get(identifier);
                if (methodLevelInterceptors != null) {
                    for (InterceptorDescription methodLevelInterceptor : methodLevelInterceptors) {
                        String interceptorClassName = methodLevelInterceptor.getInterceptorClassName();
                        List<InterceptorFactory> aroundInvokes = userAroundInvokesByInterceptorClass.get(interceptorClassName);
                        if (aroundInvokes != null) {
                            interceptorDeque.addAll(aroundInvokes);
                        }

                    }
                }

                // finally add the component level around invoke to the deque so that it's triggered last
                if (componentUserAroundInvoke != null) {
                    interceptorDeque.addAll(componentUserAroundInvoke);
                }

            }
            clazz = clazz.getSuperclass();
        }
    }

    private boolean isNotOverriden(final EEModuleClassConfiguration configuration, final Method method, final ClassReflectionIndex<?> componentClassIndex, final DeploymentReflectionIndex deploymentReflectionIndex) throws DeploymentUnitProcessingException {
        return Modifier.isPrivate(method.getModifiers()) || ClassReflectionIndexUtil.findRequiredMethod(deploymentReflectionIndex, componentClassIndex, method).getDeclaringClass() == configuration.getModuleClass();
    }
}
