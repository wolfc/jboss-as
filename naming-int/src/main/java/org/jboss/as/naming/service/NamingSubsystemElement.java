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

package org.jboss.as.naming.service;

import org.jboss.as.model.AbstractSubsystemElement;
import org.jboss.as.model.AbstractSubsystemUpdate;
import org.jboss.as.model.UpdateContext;
import org.jboss.as.model.UpdateResultHandler;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

import javax.xml.stream.XMLStreamException;
import java.util.List;

/**
 * Subsystem element representing the naming subsystem.
 *
 * @author John E. Bailey
 */
final class NamingSubsystemElement extends AbstractSubsystemElement<NamingSubsystemElement> {

    private static final long serialVersionUID = -5701304143558865658L;

    /**
     * Create a new instance.
     */
    public NamingSubsystemElement() {
        super(NamingExtension.NAMESPACE);
    }

    /** {@inheritDoc} */
    @Override
    protected Class<NamingSubsystemElement> getElementClass() {
        return NamingSubsystemElement.class;
    }

    /** {@inheritDoc} */
    @Override
    public void writeContent(final XMLExtendedStreamWriter streamWriter) throws XMLStreamException {
        streamWriter.writeEndElement();
    }

    /** {@inheritDoc} */
    protected void getUpdates(List<? super AbstractSubsystemUpdate<NamingSubsystemElement, ?>> list) {
        // none
    }

    /** {@inheritDoc} */
    protected boolean isEmpty() {
        return true;
    }

    protected NamingSubsystemAdd getAdd() {
        return new NamingSubsystemAdd();
    }

    protected <P> void applyRemove(final UpdateContext updateContext, final UpdateResultHandler<? super Void, P> resultHandler, final P param) {
        // require a restart!
    }
}
