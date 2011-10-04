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

package org.jboss.as.testsuite.integration.ejb.remote.client.api;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.testsuite.integration.ejb.remote.common.AnonymousCallbackHandler;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.remoting.IoFutureHelper;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;

import javax.ejb.NoSuchEJBException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * User: jpai
 */
@RunWith(Arquillian.class)
@RunAsClient
public class EJBClientAPIUsageTestCase {

    private static final Logger logger = Logger.getLogger(EJBClientAPIUsageTestCase.class);

    private static Connection connection;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String APP_NAME = "ejb-remote-client-api-test";

    private static final String MODULE_NAME = "ejb";

    private EJBClientContext ejbClientContext;

    @Deployment
    public static Archive<?> createDeployment() {
        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, APP_NAME + ".ear");

        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, MODULE_NAME + ".jar");
        jar.addPackage(EJBClientAPIUsageTestCase.class.getPackage());
        jar.addClass(AnonymousCallbackHandler.class);

        ear.addAsModule(jar);

        return ear;
    }


    @BeforeClass
    public static void beforeTestClass() throws Exception {
        final Endpoint endpoint = Remoting.createEndpoint("endpoint", Executors.newSingleThreadExecutor(), OptionMap.EMPTY);
        final Xnio xnio = Xnio.getInstance();
        final Registration registration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(xnio), OptionMap.create(Options.SSL_ENABLED, false));


        // open a connection
        final IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://localhost:9999"), OptionMap.create(Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE), new AnonymousCallbackHandler());
        connection = IoFutureHelper.get(futureConnection, 5, TimeUnit.SECONDS);
    }

    @AfterClass
    public static void afterTestClass() throws Exception {
        executor.shutdown();
    }

    @Before
    public void beforeTest() throws Exception {
        this.ejbClientContext = EJBClientContext.create();
        this.ejbClientContext.registerConnection(connection);
    }

    @After
    public void afterTest() throws Exception {
        if (this.ejbClientContext != null) {
            EJBClientContext.suspendCurrent();
        }
    }

    @Test
    public void testRemoteSLSBInvocation() throws Exception {
        final EchoRemote proxy = EJBClient.getProxy(APP_NAME, MODULE_NAME, null, EchoBean.class.getSimpleName(), EchoRemote.class);
        Assert.assertNotNull("Received a null proxy", proxy);
        final String message = "Hello world from a really remote client";
        final String echo = proxy.echo(message);
        Assert.assertEquals("Unexpected echo message", message, echo);
    }

    @Test
    public void testRemoteSLSBWithInterceptors() throws Exception {
        final EchoRemote proxy = EJBClient.getProxy(APP_NAME, MODULE_NAME, null, InterceptedEchoBean.class.getSimpleName(), EchoRemote.class);
        Assert.assertNotNull("Received a null proxy", proxy);
        final String message = "Hello world from a really remote client";
        final String echo = proxy.echo(message);
        final String expectedEcho = message + InterceptorTwo.MESSAGE_SEPARATOR + InterceptorOne.class.getSimpleName() + InterceptorOne.MESSAGE_SEPARATOR + InterceptorTwo.class.getSimpleName();
        Assert.assertEquals("Unexpected echo message", expectedEcho, echo);
    }

    @Test
    public void testRemoteSLSBWithCustomObjects() throws Exception {
        final EmployeeManager proxy = EJBClient.getProxy(APP_NAME, MODULE_NAME, null, EmployeeBean.class.getSimpleName(), EmployeeManager.class);
        Assert.assertNotNull("Received a null proxy", proxy);
        final String[] nickNames = new String[]{"java-programmer", "ruby-programmer", "php-programmer"};
        final Employee employee = new Employee(1, "programmer");
        // invoke on the bean
        final Employee employeeWithNickNames = proxy.addNickNames(employee, nickNames);

        // check the id of the returned employee
        Assert.assertEquals("Unexpected employee id", 1, employeeWithNickNames.getId());
        // check the name of the returned employee
        Assert.assertEquals("Unexpected employee name", "programmer", employeeWithNickNames.getName());
        // check the number of nicknames
        Assert.assertEquals("Unexpected number of nick names", nickNames.length, employeeWithNickNames.getNickNames().size());
        // make sure the correct nick names are present
        for (int i = 0; i < nickNames.length; i++) {
            Assert.assertTrue("Employee was expected to have nick name: " + nickNames[i], employeeWithNickNames.getNickNames().contains(nickNames[i]));
        }
    }

    @Test
    @Ignore("SFSB session creation hasn't been properly implemented.")
    public void testSFSBInvocation() throws Exception {
        final Counter counter = EJBClient.getProxy(APP_NAME, MODULE_NAME, null, CounterBean.class.getSimpleName(), Counter.class);
        Assert.assertNotNull("Received a null proxy", counter);
        // open a session for the SFSB
        EJBClient.createSession(counter);
        // invoke the bean
        final int initialCount = counter.getCount();
        Assert.assertEquals("Unexpected initial count from stateful bean", 0, initialCount);
        final int NUM_TIMES = 25;
        for (int i = 1; i <= NUM_TIMES; i++) {
            final int count = counter.incrementAndGetCount();
            Assert.assertEquals("Unexpected count after increment", i, count);
        }
        final int finalCount = counter.getCount();
        Assert.assertEquals("Unexpected final count", NUM_TIMES, finalCount);
    }

    @Test
    public void testNonExistentEJBAccess() throws Exception {
        final NotAnEJBInterface nonExistentBean = EJBClient.getProxy("non-existen-app-name", MODULE_NAME, null, "blah", NotAnEJBInterface.class);
        Assert.assertNotNull("Received a null proxy", nonExistentBean);
        // invoke on the (non-existent) bean
        try {
            nonExistentBean.echo("Hello world to a non-existent bean");
            Assert.fail("Expected a NoSuchEJBException");
        } catch (NoSuchEJBException nsee) {
            // expected
            logger.info("Received the expected exception", nsee);
        }
    }
}
