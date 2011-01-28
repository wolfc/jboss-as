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

package org.jboss.as.ejb3.deployment;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentException;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.logging.Logger;
import org.jboss.metadata.ejb.parser.spec.EjbJarMetaDataParser;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.parser.util.MetaDataElementParser;
import org.jboss.metadata.parser.util.NoopXmlResolver;
import org.jboss.vfs.VirtualFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;

/**
 * Processes a {@link DeploymentUnit} containing a ejb-jar.xml and creates {@link EjbJarMetaData}
 * for that unit.
 * <p/>
 * This {@link DeploymentUnitProcessor deployment unit processor} looks for ejb-jar.xml in META-INF of a .jar
 * and WEB-INF of a .war file. If it finds the ejb-jar.xml in these locations, it parses that file and creates
 * {@link EjbJarMetaData} out of it. The {@link EjbJarMetaData} is then attached to the {@link DeploymentUnit}
 * with {@link EjbDeploymentAttachmentKeys#EJB_JAR_METADATA} as the key.
 * <p/>
 * <p/>
 * Author: Jaikiran Pai
 */
public class EjbJarParsingDeploymentUnitProcessor implements DeploymentUnitProcessor {

    /**
     * Logger
     */
    private static Logger logger = Logger.getLogger(EjbJarParsingDeploymentUnitProcessor.class);

    /**
     * .war file extension
     */
    private static final String WAR_FILE_EXTENSION = ".war";

    /**
     * .jar file extension
     */
    private static final String JAR_FILE_EXTENSION = ".jar";

    /**
     * Location of ejb-jar.xml packaged in a .war
     */
    private static final String EJB_JAR_XML_LOCATION_IN_WAR = "WEB-INF/ejb-jar.xml";

    /**
     * Location of ejb-jar.xml packaged in a .jar
     */
    private static final String EJB_JAR_XML_LOCATION_IN_JAR = "META-INF/ejb-jar.xml";

    /**
     * Finds a ejb-jar.xml (at WEB-INF of a .war or META-INF of a .jar) parses the file and creates
     * metadata out of it. The metadata is then attached to the deployment unit.
     *
     * @param deploymentPhase
     * @throws DeploymentUnitProcessingException
     *
     */
    @Override
    public void deploy(DeploymentPhaseContext deploymentPhase) throws DeploymentUnitProcessingException {

        // get hold of the deployment unit.
        DeploymentUnit deploymentUnit = deploymentPhase.getDeploymentUnit();

        // get the root of the deployment unit
        VirtualFile deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot();

        // Locate a ejb-jar.xml
        VirtualFile ejbJarXml = null;
        // TODO: Is there a better way to do this?
        if (deploymentRoot.getName().toLowerCase().endsWith(WAR_FILE_EXTENSION)) {
            // it's a .war file, so look for the ejb-jar.xml in WEB-INF
            ejbJarXml = deploymentRoot.getChild(EJB_JAR_XML_LOCATION_IN_WAR);
        } else if (deploymentRoot.getName().toLowerCase().endsWith(JAR_FILE_EXTENSION)) {
            ejbJarXml = deploymentRoot.getChild(EJB_JAR_XML_LOCATION_IN_JAR);
        } else {
            // neither a .jar nor a .war. Return
            return;
        }

        if (ejbJarXml == null) {
            // no ejb-jar.xml found, nothing to do!
            return;
        }

        // get the XMLStreamReader and parse the ejb-jar.xml
        XMLStreamReader reader = this.getXMLStreamReader(ejbJarXml);
        MetaDataElementParser.DTDInfo dtdInfo = new MetaDataElementParser.DTDInfo();
        try {
            EjbJarMetaData ejbJarMetaData = EjbJarMetaDataParser.parse(reader, dtdInfo);
            // attach the ejbjar metadata
            deploymentUnit.putAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA, ejbJarMetaData);

        } catch (XMLStreamException xmlse) {
            throw new DeploymentUnitProcessingException("Exception while parsing ejb-jar.xml: " + ejbJarXml.getPathName(), xmlse);
        }

    }

    /**
     * @param unit
     */
    @Override
    public void undeploy(DeploymentUnit unit) {

    }

    /**
     * Creates and returns a {@link XMLStreamReader} for the passed {@link VirtualFile ejb-jar.xml}
     *
     * @param ejbJarXml
     * @return
     * @throws DeploymentUnitProcessingException
     *
     */
    private XMLStreamReader getXMLStreamReader(VirtualFile ejbJarXml) throws DeploymentUnitProcessingException {
        InputStream is = null;
        try {
            is = ejbJarXml.openStream();
            final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setXMLResolver(NoopXmlResolver.create());
            XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(is);
            return xmlReader;
        } catch (XMLStreamException xmlse) {
            throw new DeploymentUnitProcessingException("Failed to create reader for ejb-jar.xml: " + ejbJarXml.getPathName(), xmlse);
        } catch (IOException ioe) {
            throw new DeploymentUnitProcessingException("Failed to create reader for ejb-jar.xml: " + ejbJarXml.getPathName(), ioe);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ioe) {
                logger.debug("Ignoring exception while closing the InputStream ", ioe);
            }
        }
    }
}
