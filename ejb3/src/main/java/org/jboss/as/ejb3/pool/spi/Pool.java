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
package org.jboss.as.ejb3.pool.spi;

/**
 * A pool of component instances.
 * <p/>
 * The pool is linked to a component instance factory.
 *
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public interface Pool<T> {
    /**
     * Remove the instance from the pool and notify the
     * component instance factory.
     *
     * @param instance  the component instance
     */
    void destroy(T instance);

    /**
     * Discard an instance. This will be called
     * in case of a system exception.
     *
     * The pool is not obliged to notify the component instance factory.
     *
     * @param instance  the component instance
     */
    void discard(T instance);

    /**
     * Get the an instance from the pool. This will mark
     * the instance as being in use.
     *
     * @return the component instance
     */
    T get();

    /**
     * Release the instance from use.
     *
     * @param instance  the component instance
     */
    void release(T instance);

    /**
     * Associates the pool with the proper factory.
     * @param factory   the component instance factory
     */
    void setComponentInstanceFactory(ComponentInstanceFactory<T> factory);
}