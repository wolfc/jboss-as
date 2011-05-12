/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.weld.injection;

import org.jboss.as.naming.ManagedReference;

import javax.enterprise.context.spi.CreationalContext;
import java.util.Map;

/**
* @author Stuart Douglas
*/
class WeldManagedReference implements ManagedReference {
    private final CreationalContext<?> context;
    private final Object instance;
    private final WeldEEInjection injectionTarget;
    private final Map<Class<?>, WeldEEInjection> interceptorInjections;

    public WeldManagedReference(CreationalContext<?> ctx, Object instance, final WeldEEInjection injectionTarget, final Map<Class<?>, WeldEEInjection> interceptorInjections) {
        this.context = ctx;
        this.instance = instance;
        this.injectionTarget = injectionTarget;
        this.interceptorInjections = interceptorInjections;
    }

    /**
     * Runs CDI injection on the instance. This should be called after resource injection has been performed
     */
    public void inject() {
        injectionTarget.inject(instance, context);
    }

    public void injectInterceptor(Class<?> interceptorClass, Object instance) {
        final WeldEEInjection injection = interceptorInjections.get(interceptorClass);
        if(injection != null) {
            injection.inject(instance, context);
        } else {
            throw new IllegalArgumentException("Unknown interceptor class for CDI injection " + interceptorClass);
        }
    }

    @Override
    public void release() {
        context.release();
    }

    @Override
    public Object getInstance() {
        return instance;
    }

    public CreationalContext<?> getContext() {
        return context;
    }

    public WeldEEInjection getInjectionTarget() {
        return injectionTarget;
    }
}
