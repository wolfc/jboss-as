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
package org.jboss.as.ejb3;

import org.jboss.as.ejb3.test.parser.CacheConfigParser;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Test;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class SubsystemParserTestCase {
    @Test
    public void testParse() throws Exception {
        final XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(EJB3SubsystemParser.NAMESPACE, "subsystem"), EJB3SubsystemParser.getInstance());
        mapper.registerRootElement(new QName(CacheConfigParser.NAMESPACE, "cache-config"), new CacheConfigParser());
        InputStream stream = getClass().getResourceAsStream("/custom-subsystem.xml");
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(stream);
        List<ModelNode> result = new ArrayList<ModelNode>();
        mapper.parseDocument(result, reader);

        ModelNode subsystem = result.get(0);
        ModelNode assemblyDescriptor = subsystem.get(CommonAttributes.ASSEMBLY_DESCRIPTOR);
        assertNotNull(assemblyDescriptor);

        ModelNode cacheConfig = assemblyDescriptor.get("cache-config");
        assertNotNull(cacheConfig);
        assertEquals("*", cacheConfig.get("ejb-name").asString());
        assertEquals("jboss.cache:service=EJB3SFSBClusteredCache", cacheConfig.get("cache-name").asString());
    }
}
