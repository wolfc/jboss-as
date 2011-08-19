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

package org.jboss.as.ejb3.subsystem;

import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.dmr.ModelNode;

import java.util.Locale;

/**
 * {@link DescriptionProvider} implementations for EJB3 subsystem resources.
 *
 * @author Emanuel Muckenhuber
 */
class EJB3SubsystemProviders {

    static final DescriptionProvider REMOTE_CONNECTOR_SERVICE = new DescriptionProvider() {
        @Override
        public ModelNode getModelDescription(Locale locale) {
            return EJB3SubsystemDescriptions.getRemoteConnectorDescription(locale);
        }
    };

    static final DescriptionProvider SUBSYSTEM = new DescriptionProvider() {

        public ModelNode getModelDescription(final Locale locale) {
            return EJB3SubsystemDescriptions.getSubystemDescription(locale);
        }
    };

    public static final DescriptionProvider STRICT_MAX_BEAN_INSTANCE_POOL = new DescriptionProvider() {
        @Override
        public ModelNode getModelDescription(Locale locale) {
            return EJB3SubsystemDescriptions.getStrictMaxBeanInstancePoolDescription(locale);
        }
    };

    public static final DescriptionProvider TIMER_SERVICE = new DescriptionProvider() {
        @Override
        public ModelNode getModelDescription(Locale locale) {
            return EJB3SubsystemDescriptions.getTimerServiceDescription(locale);
        }
    };
}
