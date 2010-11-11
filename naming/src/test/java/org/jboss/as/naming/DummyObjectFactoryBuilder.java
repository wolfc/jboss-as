/*
 * JBoss, Home of Professional Open Source
 * Copyright (c) 2010, JBoss Inc., and individual contributors as indicated
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
package org.jboss.as.naming;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import javax.naming.spi.ObjectFactoryBuilder;
import java.util.Hashtable;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class DummyObjectFactoryBuilder implements ObjectFactoryBuilder
{
   @Override
   public ObjectFactory createObjectFactory(Object obj, Hashtable<?, ?> environment) throws NamingException
   {
      try
      {
         Reference ref = (Reference) obj;
         String factoryClassName = ref.getFactoryClassName();
         return (ObjectFactory) Class.forName(factoryClassName).newInstance();
      }
      catch(ClassNotFoundException e)
      {
         throw namingException(e);
      }
      catch (InstantiationException e)
      {
         throw namingException(e);
      }
      catch (IllegalAccessException e)
      {
         throw namingException(e);
      }
   }

   private NamingException namingException(Throwable t)
   {
      NamingException e = new NamingException(t.getMessage());
      e.initCause(t);
      return e;
   }
}
