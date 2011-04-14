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
package org.jboss.as.embedded.ejb3;

import org.jboss.as.embedded.EmbeddedServerFactory;
import org.jboss.as.embedded.StandaloneServer;

import javax.ejb.EJBException;
import javax.ejb.embeddable.EJBContainer;
import javax.ejb.spi.EJBContainerProvider;
import java.io.File;
import java.util.Map;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class JBossStandaloneEJBContainerProvider implements EJBContainerProvider {
    public static final String JBOSS_EMBEDDED_USER_PKGS = "jboss.embedded.user.pkgs";

    @Override
    public EJBContainer createEJBContainer(Map<?, ?> properties) throws EJBException {
        String jbossHomeKey = "jboss.home";
        String jbossHomeProp = System.getProperty(jbossHomeKey);
        if (jbossHomeProp == null)
            throw new EJBException("Cannot find system property: " + jbossHomeKey);

        File jbossHomeDir = new File(jbossHomeProp);
        if (jbossHomeDir.isDirectory() == false)
            throw new EJBException("Invalid jboss home directory: " + jbossHomeDir);

        // make sure we always have the app cl as a dependency
        // see DefaultBootModuleLoaderHolder
        System.setProperty("boot.module.loader", EmbeddedModuleLoader.class.getName());

        // TODO: normally we would not have org.jboss.logmanager on this side of the fence
        StringBuffer packages = new StringBuffer("org.jboss.logmanager");
        // TODO: how are we going to determine which facilities are on which side of the fence? An user can do everything.
        final String userPackages = property(properties, JBOSS_EMBEDDED_USER_PKGS);
        if (userPackages != null) {
            packages.append("," + userPackages);
        }
        final StandaloneServer server = EmbeddedServerFactory.create(jbossHomeDir, System.getProperties(), System.getenv(), packages.toString());
        try {
            server.start();
            final JBossStandaloneEJBContainer container = new JBossStandaloneEJBContainer(server);
            boolean okay = false;
            try {
                container.init();
                okay = true;
                return container;
            }
            finally {
                if (!okay)
                    container.close();
            }
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new EJBException(e);
        }
    }

    private static String property(final Map<?, ?> properties, final String key) {
        return System.getProperty(key, properties == null ? null : (String) properties.get(key));
    }
}
