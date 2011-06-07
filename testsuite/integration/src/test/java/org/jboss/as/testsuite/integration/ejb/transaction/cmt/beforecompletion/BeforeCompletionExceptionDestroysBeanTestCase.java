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
package org.jboss.as.testsuite.integration.ejb.transaction.cmt.beforecompletion;

import org.jboss.arquillian.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.NoSuchEJBException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import static org.junit.Assert.fail;

/**
 * Test that ensures that a SFSB is destroyed if an exception is thrown by it's @BeforeCompletion method
 *
 * @author Stuart Douglas
 */
@RunWith(Arquillian.class)
public class BeforeCompletionExceptionDestroysBeanTestCase {

    @Deployment
    public static Archive<?> deploy() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "test-beforecompletion.war");
        war.addPackage(BeforeCompletionExceptionDestroysBeanTestCase.class.getPackage());
        return war;
    }

    @Test
    public void testExceptionInBeforeCompletionDestroysBean() throws NamingException, SystemException, NotSupportedException,  HeuristicRollbackException, HeuristicMixedException {
        final UserTransaction userTransaction = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        final BeforeCompletionSFSB bean = (BeforeCompletionSFSB) new InitialContext().lookup("java:module/" + BeforeCompletionSFSB.class.getSimpleName());
        //begin a transaction
        userTransaction.begin();
        bean.enlist();
        //commit, this should destroy the bean, as it's @BeforeCompletion method will throw an exception
        try {
            userTransaction.commit();
            fail("Expected RollbackException");
        } catch (RollbackException expected ) {

        }
        try {
            userTransaction.begin();
            bean.enlist();
            fail("Expected NoSuchEJBException (EJB 3.1 FR 14.3.7)");
        } catch (NoSuchEJBException expected) {

        } finally {
            userTransaction.rollback();
        }
    }

    @Test
    public void testExceptionInBusinessInvocation() throws NamingException, SystemException, NotSupportedException,  HeuristicRollbackException, HeuristicMixedException {
        final UserTransaction userTransaction = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        final BarfSFSB bean = (BarfSFSB) new InitialContext().lookup("java:module/" + BarfSFSB.class.getSimpleName());
        //begin a transaction
        userTransaction.begin();
        try {
            bean.enlist();
            bean.barf();
            fail("Expected EJBTransactionRolledbackException (EJB 3.1 FR 14.3.1)");
        } catch (EJBTransactionRolledbackException e) {
        }
        userTransaction.rollback();
        try {
            userTransaction.begin();
            bean.enlist();
            fail("Expected NoSuchEJBException (EJB 3.1 FR 14.3.7)");
        } catch (NoSuchEJBException expected) {

        } finally {
            userTransaction.rollback();
        }
    }

}
