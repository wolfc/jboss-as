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
package org.jboss.as.ejb3.test.parser;

import org.jboss.as.model.ParseResult;
import org.jboss.as.model.ParseUtils;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

import javax.xml.stream.XMLStreamException;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class CacheConfigParser implements XMLElementReader<ParseResult<CacheConfig>> {
   public static final String NAMESPACE = Namespace.TEST_1_0.getUriString();

   @Override
   public void readElement(XMLExtendedStreamReader reader, ParseResult<CacheConfig> result)
           throws XMLStreamException {
      CacheConfig value = new CacheConfig();
      while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
         switch (Namespace.forUri(reader.getNamespaceURI())) {
            case TEST_1_0:
               final Element element = Element.forName(reader.getLocalName());
               switch (element) {
                  case CACHE_NAME:
                     value.setCacheName(reader.getElementText());
                     break;
                  case EJB_NAME:
                     value.setEjbName(reader.getElementText());
                     break;
                  default:
                     throw ParseUtils.unexpectedElement(reader);
               }
               break;
            default:
               throw ParseUtils.unexpectedElement(reader);
         }
      }
      result.setResult(value);
   }
}
