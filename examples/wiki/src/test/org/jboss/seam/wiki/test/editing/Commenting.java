/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.seam.wiki.test.editing;

import org.dbunit.operation.DatabaseOperation;
import org.jboss.seam.wiki.core.action.CommentHome;
import org.jboss.seam.wiki.core.action.CommentQuery;
import org.jboss.seam.wiki.core.model.User;
import org.jboss.seam.wiki.core.model.WikiDocument;
import org.jboss.seam.wiki.core.model.WikiDirectory;
import org.jboss.seam.wiki.core.model.WikiComment;
import org.jboss.seam.wiki.core.dao.WikiNodeDAO;
import org.jboss.seam.wiki.test.util.DBUnitSeamTest;
import org.jboss.seam.contexts.Contexts;
import org.testng.annotations.Test;

public class Commenting extends DBUnitSeamTest {

    protected void prepareDBUnitOperations() {
        beforeTestOperations.add(
            new DataSetOperation("org/jboss/seam/wiki/test/WikiBaseData.dbunit.xml", DatabaseOperation.CLEAN_INSERT)
        );
    }

    @Test
    public void postComment() throws Exception {

        new FacesRequest("/docDisplay_d.xhtml") {

            protected void beforeRequest() {
                setParameter("documentId", "6");
            }

            protected void updateModelValues() throws Exception {
                assert getValue("#{documentHome.instance.id}").equals(6l);
            }

            protected void invokeApplication() throws Exception {

                CommentHome commentHome = (CommentHome)getInstance(CommentHome.class);

                commentHome.newComment();

                commentHome.getInstance().setFromUserName("Foo");
                commentHome.getInstance().setFromUserHomepage("http://foo.bar");
                commentHome.getInstance().setFromUserEmail("foo@bar.tld");
                commentHome.getInstance().setSubject("Some Subject");
                commentHome.getInstance().setContent("Some Content");

                invokeMethod("#{commentHome.persist}");
            }

            protected void renderResponse() throws Exception {
                CommentQuery commentQuery = (CommentQuery)getInstance(CommentQuery.class);
                assert commentQuery.getComments().size() == 7;

                assert commentQuery.getComments().get(0).getId().equals(10l);
                assert commentQuery.getComments().get(1).getId().equals(11l);
                assert commentQuery.getComments().get(2).getId().equals(12l);
                assert commentQuery.getComments().get(3).getId().equals(13l);
                assert commentQuery.getComments().get(4).getId().equals(14l);
                assert commentQuery.getComments().get(5).getId().equals(15l);

                assert commentQuery.getComments().get(6).getCreatedBy().getUsername().equals(User.GUEST_USERNAME);
                assert commentQuery.getComments().get(6).getFromUserName().equals("Foo");
                assert commentQuery.getComments().get(6).getFromUserHomepage().equals("http://foo.bar");
                assert commentQuery.getComments().get(6).getFromUserEmail().equals("foo@bar.tld");
                assert commentQuery.getComments().get(6).getSubject().equals("Some Subject");
                assert commentQuery.getComments().get(6).getContent().equals("Some Content");

                assert commentQuery.getComments().get(6).getName().matches("One\\.Comment[0-9]+");
                assert !commentQuery.getComments().get(6).getWikiname().contains(" ");
                
            }

        }.run();
    }

    @Test
    public void deleteComment() throws Exception {

        loginAdmin();

        new FacesRequest("/docDisplay_d.xhtml") {

            protected void beforeRequest() {
                setParameter("documentId", "6");
            }

            protected void updateModelValues() throws Exception {
                assert getValue("#{documentHome.instance.id}").equals(6l);
            }

            protected void invokeApplication() throws Exception {
                invokeMethod("#{commentHome.remove(12)}");
            }

            protected void renderResponse() throws Exception {
                CommentQuery commentQuery = (CommentQuery)getInstance(CommentQuery.class);
                assert commentQuery.getComments().size() == 4;
                assert commentQuery.getComments().get(0).getId().equals(10l);
                assert commentQuery.getComments().get(0).getLevel().equals(1l);
                assert commentQuery.getComments().get(1).getId().equals(11l);
                assert commentQuery.getComments().get(1).getLevel().equals(2l);
                assert commentQuery.getComments().get(2).getId().equals(14l);
                assert commentQuery.getComments().get(2).getLevel().equals(1l);
                assert commentQuery.getComments().get(3).getId().equals(15l);
                assert commentQuery.getComments().get(3).getLevel().equals(2l);
            }

        }.run();
    }
    
    private void loginAdmin() throws Exception {
        new FacesRequest() {
           protected void invokeApplication() throws Exception {
              setValue("#{identity.username}", "admin");
              setValue("#{identity.password}", "admin");
              invokeAction("#{identity.login}");
              assert getValue("#{identity.loggedIn}").equals(true);
           }
        }.run();
    }


}